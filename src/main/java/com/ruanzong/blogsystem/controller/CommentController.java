package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.entity.Comment;
import com.ruanzong.blogsystem.entity.DiscussPost;
import com.ruanzong.blogsystem.entity.Event;
import com.ruanzong.blogsystem.event.EventProducer;
import com.ruanzong.blogsystem.service.CommentService;
import com.ruanzong.blogsystem.service.DiscussPostService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.HostHolder;
import com.ruanzong.blogsystem.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

   @RestController
@RequestMapping("/api/comments")
public class CommentController implements CommunityConstant{
       @Autowired
       private HostHolder hostHolder;

       @Autowired
       private CommentService commentService;

       @Autowired
       private DiscussPostService discussPostService;
       @Autowired
       private EventProducer eventProducer;

       @Autowired
       private RedisTemplate redisTemplate;


    @PostMapping("/add/{discussPostId}")
    public ResponseEntity<String> addComment(@PathVariable("discussPostId") int discussPostId, @RequestBody Comment comment) {
        comment.setUserId(hostHolder.getUser().getId());
        comment.setStatus(0);
        comment.setCreateTime(new Date());
        commentService.addComment(comment);

        // 触发评论事件（系统通知）
        Event event = new Event()
                .setTopic(TOPIC_COMMNET)
                .setUserId(hostHolder.getUser().getId())
                .setEntityType(comment.getEntityType())
                .setEntityId(comment.getEntityId())
                .setData("postId", discussPostId);
        if (comment.getEntityType() == ENTITY_TYPE_POST) {
            DiscussPost target = discussPostService.findDiscussPostById(comment.getEntityId());
            event.setEntityUserId(target.getUserId());
        }
        else if (comment.getEntityType() == ENTITY_TYPE_COMMENT) {
            Comment target = commentService.findCommentById(comment.getEntityId());
            event.setEntityUserId(target.getUserId());
        }
        eventProducer.fireEvent(event);

        if (comment.getEntityType() == ENTITY_TYPE_POST) {
            // 触发发帖事件，通过消息队列将其存入 Elasticsearch 服务器
            event = new Event()
                    .setTopic(TOPIC_PUBLISH)
                    .setUserId(comment.getUserId())
                    .setEntityType(ENTITY_TYPE_POST)
                    .setEntityId(discussPostId);
            eventProducer.fireEvent(event);

            // 计算帖子分数
            String redisKey = RedisKeyUtil.getPostScoreKey();
            redisTemplate.opsForSet().add(redisKey, discussPostId);
        }

        JSONObject res = new JSONObject();
        res.put("discussPostId", discussPostId);
        // 返回成功的响应
        return ResponseEntity.ok(CommunityUtil.getJSONString(
                HTTP_OK, "操作成功" ,res
        ));
    }
}
