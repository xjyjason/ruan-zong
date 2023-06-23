package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.entity.Event;
import com.ruanzong.blogsystem.entity.User;
import com.ruanzong.blogsystem.event.EventProducer;
import com.ruanzong.blogsystem.service.LikeService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.HostHolder;
import com.ruanzong.blogsystem.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 点赞
 */
@RestController
@RequestMapping("/api/likes")
public class LikeController implements CommunityConstant {

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private LikeService likeService;

    @Autowired
    private EventProducer eventProducer;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 点赞
     * @param entityType
     * @param entityId
     * @param entityUserId 赞的帖子/评论的作者 id
     * @param postId 帖子的 id (点赞了哪个帖子，点赞的评论属于哪个帖子，点赞的回复属于哪个帖子)
     * @return
     */
    @PostMapping("/to-like")
    public ResponseEntity<String> like(@RequestBody JSONObject likeMessage) {
        User user = hostHolder.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommunityUtil.getJSONString(403, "您还未登录"));
        }
        Object _entityType = likeMessage.get("entityType");
        Object _entityId = likeMessage.get("entityId");
        Object _entityUserId = likeMessage.get("entityUserId");
        Object _postId = likeMessage.get("postId");
        if(_postId == null || _entityId == null || _entityType == null || _entityUserId == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "请输入信息"));
        }
        int entityType = (int) _entityType;
        int entityId = (int) _entityId;
        int entityUserId = (int) _entityUserId;
        int postId = (int) _postId;
        // 点赞
        likeService.like(user.getId(), entityType, entityId, entityUserId);
        // 点赞数量
        long likeCount = likeService.findEntityLikeCount(entityType, entityId);
        // 点赞状态
        int likeStatus = likeService.findEntityLikeStatus(user.getId(), entityType, entityId);

        Map<String, Object> map = new HashMap<>();
        map.put("likeCount", likeCount);
        map.put("likeStatus", likeStatus);

        // 触发点赞事件（系统通知） - 取消点赞不通知
        if (likeStatus == 1) {
            Event event = new Event()
                    .setTopic(TOPIC_LIKE)
                    .setUserId(hostHolder.getUser().getId())
                    .setEntityType(entityType)
                    .setEntityId(entityId)
                    .setEntityUserId(entityUserId)
                    .setData("postId", postId);
            eventProducer.fireEvent(event);
        }

        if (entityType == ENTITY_TYPE_POST) {
            // 计算帖子分数
            String redisKey = RedisKeyUtil.getPostScoreKey();
            redisTemplate.opsForSet().add(redisKey, postId);
        }

        return ResponseEntity.ok().body(CommunityUtil.getJSONString(200, "点赞成功", map));
    }

}
