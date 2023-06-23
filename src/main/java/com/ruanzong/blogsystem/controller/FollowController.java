package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.entity.Event;
import com.ruanzong.blogsystem.entity.Page;
import com.ruanzong.blogsystem.entity.User;
import com.ruanzong.blogsystem.event.EventProducer;
import com.ruanzong.blogsystem.service.FollowService;
import com.ruanzong.blogsystem.service.UserService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.HostHolder;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关注
 */
@RestController
@RequestMapping("/api/follow")
public class FollowController implements CommunityConstant {

    @Autowired
    private FollowService followService;

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private UserService userService;

    @Autowired
    private EventProducer eventProducer;

    @PostMapping("/to-follow")
    public ResponseEntity<String> follow(@RequestBody JSONObject entity) {
        User user = hostHolder.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommunityUtil.getJSONString(403, "您还未登录"));
        }

        Object _entityType = entity.get("entity_type");
        Object _entityId = entity.get("entity_id");
        if (_entityId == null || _entityType == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "请求失败"));
        }
        int entityId = (int)_entityId;
        int entityType = (int)_entityType;
        followService.follow(user.getId(), entityType, entityId);

        // 触发关注事件（系统通知）
        Event event = new Event()
                .setTopic(TOPIC_FOLLOW)
                .setUserId(user.getId())
                .setEntityType(entityType)
                .setEntityId(entityId)
                .setEntityUserId(entityId);
        eventProducer.fireEvent(event);

        return ResponseEntity.ok().body(CommunityUtil.getJSONString(200, "已关注"));
    }

    @PostMapping("/to-unfollow")
    public ResponseEntity<String> unfollow(@RequestBody JSONObject entity) {
        User user = hostHolder.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(403, "您还未登录！"));
        }
        Object _entityType = entity.get("entity_type");
        Object _entityId = entity.get("entity_id");
        if (_entityId == null || _entityType == null){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "请求失败"));
        }
        int entityId = (int)_entityId;
        int entityType = (int)_entityType;
        followService.unfollow(user.getId(), entityType, entityId);

        return ResponseEntity.ok().body(CommunityUtil.getJSONString(200, "已取消关注"));
    }

    /**
     * 某个用户的关注列表（人）
     * @param userId
     * @param page
     * @param model
     * @return
     */
    @GetMapping("/followees/{userId}/{page}")
    public ResponseEntity<String> getFollowees(@PathVariable("userId") int userId, @PathVariable("page") int page) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommunityUtil.getJSONString(404, "该用户不存在"));
        }

        int followCount = (int)followService.findFolloweeCount(userId, ENTITY_TYPE_USER);
        int offset = (page-1)/PageLimit;
        // 获取关注列表
        List<Map<String, Object>> userList = followService.findFollowees(userId, offset, PageLimit);

        if (userList != null) {
            for (Map<String, Object> map : userList) {
                User u = (User) map.get("user"); // 被关注的用户
                map.put("hasFollowed", hasFollowed(u.getId())); // 判断当前登录用户是否已关注这个关注列表中的某个用户
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("page_current", page);
        result.put("follows", userList);
        result.put("page_current", followCount);
        return ResponseEntity.ok().body(CommunityUtil.getJSONString(200, "获取关注列表成功", result));
    }

    /**
     * 某个用户的粉丝列表
     * @param userId
     * @param page
     * @param model
     * @return
     */
    @GetMapping("/followers/{userId}/{page}")
    public ResponseEntity<String> getFollowers(@PathVariable("userId") int userId, @PathVariable("page") int page) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommunityUtil.getJSONString(404, "该用户不存在"));
        }

        int followerCount = (int) followService.findFollowerCount(ENTITY_TYPE_USER, userId);
        int offset = (page-1) / PageLimit;

        // 获取关注列表
        List<Map<String, Object>> userList = followService.findFollowers(userId, offset, PageLimit);

        if (userList != null) {
            for (Map<String, Object> map : userList) {
                User u = (User) map.get("user"); // 被关注的用户
                map.put("hasFollowed", hasFollowed(u.getId())); // 判断当前登录用户是否已关注这个关注列表中的某个用户
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("page", page);
        result.put("users", userList);
        result.put("follwer_cnt", followerCount);

        return ResponseEntity.ok().body(CommunityUtil.getJSONString(0, "获取粉丝列表成功", result));
    }


    /**
     * 判断当前登录用户是否已关注某个用户
     * @param userId 某个用户
     * @return
     */
    private boolean hasFollowed(int userId) {
        if (hostHolder.getUser() == null) {
            return false;
        }

        return followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
    }

}
