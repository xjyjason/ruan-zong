package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.entity.*;
import com.ruanzong.blogsystem.event.EventProducer;
import com.ruanzong.blogsystem.service.CommentService;
import com.ruanzong.blogsystem.service.DiscussPostService;
import com.ruanzong.blogsystem.service.LikeService;
import com.ruanzong.blogsystem.service.UserService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.HostHolder;
import com.ruanzong.blogsystem.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.io.File;
import java.util.*;

/**
 * 帖子
 */
@RestController
@RequestMapping("/api/discuss")
public class DiscussPostController implements CommunityConstant {

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private UserService userService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${community.path.domain}")
    private String domain;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Value("${community.path.editormdUploadPath}")
    private String editormdUploadPath;

    @PostMapping("/uploadMdPic")
    public ResponseEntity<String> uploadMdPic(@RequestParam(value = "editormd-image-file", required = false) MultipartFile file) {
        String url;
        try {
            // 获取上传文件的名称
            String trueFileName = file.getOriginalFilename();
            String suffix = trueFileName.substring(trueFileName.lastIndexOf("."));
            String fileName = CommunityUtil.generateUUID() + suffix;

            // 图片存储路径
            File dest = new File(editormdUploadPath + "/" + fileName);
            if (!dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }

            // 保存图片到存储路径
            file.transferTo(dest);

            // 图片访问地址
            url = domain + contextPath + "/editor-md-upload/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getEditorMdJSONString(0, "上传失败", null));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CommunityUtil.getEditorMdJSONString(1, "上传成功", url));
    }

    @PostMapping("/add")
    public ResponseEntity<String> addDiscussPost(@RequestBody JSONObject post) {

        User user = hostHolder.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommunityUtil.getJSONString(403, "您还未登录"));
        }
        String title = (String)post.get("title");
        String content = (String)post.get("content");
        if(title == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "标题为空"));
        }

        DiscussPost discussPost = new DiscussPost();
        discussPost.setUserId(user.getId());
        discussPost.setTitle(title);
        discussPost.setContent(content);
        discussPost.setCreateTime(new Date());

        discussPostService.addDiscussPost(discussPost);

        // 触发发帖事件，通过消息队列将其存入 Elasticsearch 服务器
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(user.getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(discussPost.getId());
        eventProducer.fireEvent(event);

        // 计算帖子分数
        String redisKey = RedisKeyUtil.getPostScoreKey();
        redisTemplate.opsForSet().add(redisKey, discussPost.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CommunityUtil.getJSONString(0, "发布成功"));
    }

    /**
     * 获取帖子详情
     * @param discussPostId
     * @return
     */
    @GetMapping("/detail/{discussPostId}")
    public ResponseEntity<String> getDiscussPost(@PathVariable("discussPostId") int discussPostId) {
        // 帖子
        DiscussPost discussPost = discussPostService.findDiscussPostById(discussPostId);
        if(discussPost == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "帖子id不存在"));
        }
        String content = HtmlUtils.htmlUnescape(discussPost.getContent()); // 内容反转义，不然 markDown 格式无法显示
        discussPost.setContent(content);
        JSONObject res = new JSONObject();
        res.put("post", discussPost);
        // 作者
        User user = userService.findUserById(discussPost.getUserId());
        res.put("user", user);
        // 点赞数量
        long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, discussPostId);
        res.put("like_cnt", likeCount);
        // 当前登录用户的点赞状态
        int likeStatus = hostHolder.getUser() == null ? 0 :
                likeService.findEntityLikeStatus(hostHolder.getUser().getId(), ENTITY_TYPE_POST, discussPostId);
        res.put("like_status", likeStatus);

        return  ResponseEntity.ok(CommunityUtil.getJSONString(200, "操作成功",res));
    }

    @GetMapping("/comments")
    public  ResponseEntity<String> getComments(
            @RequestParam(value="discussPostId", required=true) int discussPostId,
            @RequestParam(value="pageId", required=true) int pageId) {

        JSONObject res = new JSONObject();
        DiscussPost discussPost = discussPostService.findDiscussPostById(discussPostId);
        if(discussPost == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "帖子id不存在"));
        }
        int pageCount = commentService.findCommentCount(
                ENTITY_TYPE_POST, discussPost.getId())/PageLimit;
        // 帖子的评论列表
        List<Comment> commentList = commentService.findCommentByEntity(
                ENTITY_TYPE_POST, discussPost.getId(), (pageId-1)*PageLimit, PageLimit);

        // 封装评论及其相关信息
        List<JSONObject> commentVoList = new ArrayList<>();
        long likeCount;
        int likeStatus;
        if (commentList != null) {
            for (Comment comment : commentList) {
                // 存储对帖子的评论
                JSONObject commentVo = new JSONObject();
                commentVo.put("comment", comment); // 评论
                commentVo.put("user", userService.findUserById(comment.getUserId())); // 发布评论的作者
                likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, comment.getId()); // 该评论点赞数量
                commentVo.put("like_cnt", likeCount);
                likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(
                        hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, comment.getId()); // 当前登录用户对该评论的点赞状态
                commentVo.put("like_status", likeStatus);


                // 存储每个评论对应的回复（不做分页）
                List<Comment> replyList = commentService.findCommentByEntity(
                        ENTITY_TYPE_COMMENT, comment.getId(), 0, Integer.MAX_VALUE);
                List<JSONObject> replyVoList = new ArrayList<>(); // 封装对评论的评论和评论的作者信息
                if (replyList != null) {
                    for (Comment reply : replyList) {
                        JSONObject replyVo = new JSONObject();
                        replyVo.put("reply", reply); // 回复
                        replyVo.put("user", userService.findUserById(reply.getUserId())); // 发布该回复的作者
                        User target = reply.getTargetId() == 0 ? null : userService.findUserById(reply.getTargetId());
                        replyVo.put("target", target); // 该回复的目标用户
                        likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_COMMENT, reply.getId());
                        replyVo.put("like_cnt", likeCount); // 该回复的点赞数量
                        likeStatus = hostHolder.getUser() == null ? 0 : likeService.findEntityLikeStatus(
                                hostHolder.getUser().getId(), ENTITY_TYPE_COMMENT, reply.getId());
                        replyVo.put("like_status", likeStatus); // 当前登录用户的点赞状态

                        replyVoList.add(replyVo);
                    }
                }
                commentVo.put("replies", replyVoList);

                // 每个评论对应的回复数量
                int replyCount = commentService.findCommentCount(ENTITY_TYPE_COMMENT, comment.getId());
                commentVo.put("reply_cnt", replyCount);

                commentVoList.add(commentVo);
            }
            res.put("comments", commentVoList);
            res.put("page_cnt", pageCount);
            res.put("page_current", pageId);
            return ResponseEntity.ok(CommunityUtil.getJSONString(200, "获取成功", res));
        }else{
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400,"获取失败"));
        }


    }

    @PostMapping("/top")
    public ResponseEntity<String> updateTop(@RequestBody JSONObject post) {
        Object _id = post.get("id");
        if(_id == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "帖子id为空"));
        }
        int id = (int)_id;
        discussPostService.updateType(id, 1);

        // 触发发帖事件，通过消息队列将其存入 Elasticsearch 服务器
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        return ResponseEntity.ok(CommunityUtil.getJSONString(0, "置顶成功"));
    }

    @PostMapping("/wonderful")
    public ResponseEntity<String> setWonderful(@RequestBody JSONObject post) {
        Object _id = post.get("id");
        if(_id == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "帖子id为空"));
        }
        int id = (int)_id;
        discussPostService.updateStatus(id, 1);

        // 触发发帖事件，通过消息队列将其存入 Elasticsearch 服务器
        Event event = new Event()
                .setTopic(TOPIC_PUBLISH)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        // 计算帖子分数
        String redisKey = RedisKeyUtil.getPostScoreKey();
        redisTemplate.opsForSet().add(redisKey, id);

        return ResponseEntity.ok(CommunityUtil.getJSONString(0, "加精成功"));
    }

    @PostMapping("/delete")
    public ResponseEntity<String> setDelete(@RequestBody JSONObject post) {
        Object _id = post.get("id");
        if(_id == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "帖子id为空"));
        }
        int id = (int)_id;
        discussPostService.updateStatus(id, 2);

        // 触发删帖事件，通过消息队列更新 Elasticsearch 服务器
        Event event = new Event()
                .setTopic(TOPIC_DELETE)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(ENTITY_TYPE_POST)
                .setEntityId(id);
        eventProducer.fireEvent(event);

        return ResponseEntity.ok(CommunityUtil.getJSONString(0, "删帖成功"));
    }

}
