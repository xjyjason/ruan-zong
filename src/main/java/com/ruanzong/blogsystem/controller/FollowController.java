package com.ruanzong.blogsystem.controller;

import com.ruanzong.blogsystem.entity.Event;
import com.ruanzong.blogsystem.entity.Page;
import com.ruanzong.blogsystem.entity.User;
import com.ruanzong.blogsystem.event.EventProducer;
import com.ruanzong.blogsystem.service.FollowService;
import com.ruanzong.blogsystem.service.UserService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    @PostMapping("/follow")
    public String follow(int entityType, int entityId) {
        User user = hostHolder.getUser();
        if (user == null) {
            return CommunityUtil.getJSONString(403, "您还未登录");
        }

        followService.follow(user.getId(), entityType, entityId);

        // 触发关注事件（系统通知）
        Event event = new Event()
                .setTopic(TOPIC_FOLLOW)
                .setUserId(user.getId())
                .setEntityType(entityType)
                .setEntityId(entityId)
                .setEntityUserId(entityId);
        eventProducer.fireEvent(event);

        return CommunityUtil.getJSONString(0, "已关注");
    }

    @PostMapping("/unfollow")
    public String unfollow(int entityType, int entityId) {
        User user = hostHolder.getUser();
        if (user == null) {
            return CommunityUtil.getJSONString(403, "您还未登录");
        }

        followService.unfollow(user.getId(), entityType, entityId);

        return CommunityUtil.getJSONString(0, "已取消关注");
    }

    @GetMapping("/followees/{userId}")
    public String getFollowees(@PathVariable("userId") int userId, Page page) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return CommunityUtil.getJSONString(404, "该用户不存在");
        }

        page.setLimit(5);
        page.setPath("/api/follow/followees/" + userId);
        page.setRows((int) followService.findFolloweeCount(userId, ENTITY_TYPE_USER));

        // 获取关注列表
        List<Map<String, Object>> userList = followService.findFollowees(userId, page.getOffset(), page.getLimit());

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

        return CommunityUtil.getJSONString(0, "获取关注列表成功", result);
    }

    @GetMapping("/followers/{userId}")
    public String getFollowers(@PathVariable("userId") int userId, Page page) {
        User user = userService.findUserById(userId);
        if (user == null) {
            return CommunityUtil.getJSONString(404, "该用户不存在");
        }

        page.setLimit(5);
        page.setPath("/api/follow/followers/" + userId);
        page.setRows((int) followService.findFollowerCount(ENTITY_TYPE_USER, userId));

        // 获取关注列表
        List<Map<String, Object>> userList = followService.findFollowers(userId, page.getOffset(), page.getLimit());

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

        return CommunityUtil.getJSONString(0, "获取粉丝列表成功", result);
    }

    private boolean hasFollowed(int userId) {
        if (hostHolder.getUser() == null) {
            return false;
        }

        return followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId);
    }

}
