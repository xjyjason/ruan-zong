package com.ruanzong.blogsystem.controller;

import com.ruanzong.blogsystem.entity.Comment;
import com.ruanzong.blogsystem.entity.DiscussPost;
import com.ruanzong.blogsystem.entity.Page;
import com.ruanzong.blogsystem.entity.User;
import com.ruanzong.blogsystem.service.*;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.HostHolder;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import java.util.*;


/**
 * 用户
 */
@RestController
@RequestMapping("/user")
public class UserController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private LikeService likeService;

    @Autowired
    private FollowService followService;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private CommentService commentService;

    // 网站域名
    @Value("${community.path.domain}")
    private String domain;

    // 项目名(访问路径)
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Value("${qiniu.key.access}")
    private String accessKey;

    @Value("${qiniu.key.secret}")
    private String secretKey;

    @Value("${qiniu.bucket.header.name}")
    private String headerBucketName;

    @Value("${qiniu.bucket.header.url}")
    private String headerBucketUrl;

    // ...

    /**
     * 跳转至账号设置界面
     * @return
     */
    @GetMapping("/setting")
    public ResponseEntity<Map<String, Object>> getSettingPage() {
        Map<String, Object> response = new HashMap<>();

        // Generate upload file name
        String fileName = CommunityUtil.generateUUID();
        response.put("fileName", fileName);

        // Set response information for Qiniu
        StringMap policy = new StringMap();
        policy.put("returnBody", CommunityUtil.getJSONString(0));
        Auth auth = Auth.create(accessKey, secretKey);
        String uploadToken = auth.uploadToken(headerBucketName, fileName, 3600, policy);
        response.put("uploadToken", uploadToken);

        return ResponseEntity.ok(response);
    }

    /**
     * 更新图像路径（将本地的图像路径更新为云服务器上的图像路径）
     * @param fileName
     * @return
     */
    @PostMapping("/header/url")
    public ResponseEntity<String> updateHeaderUrl(@RequestParam("fileName") String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return ResponseEntity.badRequest()
                    .body(CommunityUtil.getJSONString(400, "文件名不能为空"));
        }

        // Update header URL in user profile
        String url = headerBucketUrl + "/" + fileName;
        userService.updateHeader(hostHolder.getUser().getId(), url);

        return ResponseEntity.ok(CommunityUtil.getJSONString(200, "更新成功"));
    }

    /**
     * 修改用户密码
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return
     */
    @PostMapping("/password")
    public ResponseEntity<String> updatePassword(
            @RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword
    ) {
        // Validate old password
        User user = hostHolder.getUser();
        String md5OldPassword = CommunityUtil.md5(oldPassword + user.getSalt());
        if (!user.getPassword().equals(md5OldPassword)) {
            return ResponseEntity.badRequest()
                    .body(CommunityUtil.getJSONString(400, "原密码错误"));
        }

        // Validate new password
        String md5NewPassword = CommunityUtil.md5(newPassword + user.getSalt());
        if (user.getPassword().equals(md5NewPassword)) {
            return ResponseEntity.badRequest()
                    .body(CommunityUtil.getJSONString(400, "新密码和原密码相同"));
        }

        // Update user password
        userService.updatePassword(user.getId(), newPassword);

        return ResponseEntity.ok(CommunityUtil.getJSONString(200, "修改成功"));
    }

    /**
     * 进入个人主页
     * @param userId 可以进入任意用户的个人主页
     * @return
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<String> getProfilePage(@PathVariable("userId") int userId) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(CommunityUtil.getJSONString(400, "该用户不存在"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("userLikeCount", likeService.findUserLikeCount(userId));
        response.put("followeeCount", followService.findFolloweeCount(userId, ENTITY_TYPE_USER));
        response.put("followerCount", followService.findFollowerCount(ENTITY_TYPE_USER, userId));
        response.put("hasFollowed", hostHolder.getUser() != null && followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId));
        response.put("tab", "profile");

        return ResponseEntity.ok(CommunityUtil.getJSONString(200, "操作成功", response));
    }

    /**
     * 进入我的帖子（查询某个用户的帖子列表）
     * @param userId
     * @param page
     * @return
     */
    @GetMapping("/discuss/{userId}/{page}")
    public ResponseEntity<String> getMyDiscussPosts(
            @PathVariable("userId") int userId, @PathVariable("page") int page
    ) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(CommunityUtil.getJSONString(400, "该用户不存在"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);

        int pageCount = discussPostService.findDiscussPostRows(userId);
        response.put("page_cnt", pageCount);
        response.put("page_current", page);

        List<DiscussPost> list = discussPostService.findDiscussPosts(userId, page, PageLimit, 0);
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if (list != null) {
            for (DiscussPost post : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", post);
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
                discussPosts.add(map);
            }
            response.put("discussPosts", discussPosts);
            return ResponseEntity.ok(CommunityUtil.getJSONString(200, "获取成功", response));
        }else {
            return ResponseEntity.badRequest().body(CommunityUtil.getJSONString(400, "获取失败"));
        }
    }

    /**
     * 进入我的评论/回复（查询某个用户的评论/回复列表）
     * @param userId
     * @param page
     * @return
     */
    @GetMapping("/comment/{userId}")
    public ResponseEntity<String> getMyComments(
            @PathVariable("userId") int userId,
            @PathVariable("page") int page
    ) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(CommunityUtil.getJSONString(400, "该用户不存在"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        int offset = (page-1)/PageLimit;
        int commentCounts = commentService.findCommentCountByUserId(userId);
        response.put("comment_cnt", commentCounts);

        List<Comment> list = commentService.findCommentByUserId(userId, offset, PageLimit);
        List<Map<String, Object>> comments = new ArrayList<>();
        if (list != null) {
            for (Comment comment : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("comment", comment);
                if (comment.getEntityType() == ENTITY_TYPE_POST) {
                    DiscussPost post = discussPostService.findDiscussPostById(comment.getEntityId());
                    map.put("post", post);
                }
                else if (comment.getEntityType() == ENTITY_TYPE_COMMENT) {
                    Comment targetComment = commentService.findCommentById(comment.getEntityId());
                    DiscussPost post = discussPostService.findDiscussPostById(targetComment.getEntityId());
                    map.put("post", post);
                }

                comments.add(map);
            }
            response.put("comments", comments);
            return ResponseEntity.ok(CommunityUtil.getJSONString(200, "获取成功", response));
        }else {
            return ResponseEntity.badRequest().body(CommunityUtil.getJSONString(400, "获取失败"));
        }

    }
}
