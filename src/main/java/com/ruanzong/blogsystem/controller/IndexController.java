package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.entity.DiscussPost;
import com.ruanzong.blogsystem.entity.Page;
import com.ruanzong.blogsystem.entity.User;
import com.ruanzong.blogsystem.service.DiscussPostService;
import com.ruanzong.blogsystem.service.LikeService;
import com.ruanzong.blogsystem.service.UserService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页
 */
@RestController
@RequestMapping("/api/posts")
public class IndexController implements CommunityConstant {

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private UserService userService;

    @Autowired
    private LikeService likeService;

    /**
     * 进入首页
     * @param model
     * @param page
     * @param orderMode 默认是 0（最新）
     * @return
     */
    @GetMapping("/{page}")
    public ResponseEntity<String> getIndexPage(@RequestParam(name = "orderMode", defaultValue = "0") int orderMode, @PathVariable("page") int page) {
        // 获取总页数
        int pageCount =  discussPostService.findDiscussPostRows(0);
        int offset = (page-1) / PageLimit;
        JSONObject res = new JSONObject();

        // 分页查询
        List<DiscussPost> list = discussPostService.findDiscussPosts(0, offset, PageLimit, orderMode);
        // 封装帖子和该帖子对应的用户信息
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if (list != null) {
            for (DiscussPost post : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", post);
                User user = userService.findUserById(post.getUserId());
                map.put("user", user);
                long likeCount = likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId());
                map.put("likeCount", likeCount);

                discussPosts.add(map);
            }
        }
        res.put("discussPosts", discussPosts);
        res.put("orderMode", orderMode);
        res.put("page_cnt", pageCount);
        res.put("page_current", page);
        return ResponseEntity.ok().body(CommunityUtil.getJSONString(200, "获取成功", res));
    }



    /**
     * 进入 500 错误界面
     * @return
     */
     @GetMapping("/error")
     public ResponseEntity<String> handleServerError() {
         String errorMessage = "服务器发生错误"; // 设置错误信息
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(CommunityUtil.getJSONString(500, errorMessage));
     }


    /**
     * 没有权限访问时的错误界面（也是 404）
     * @return
     */
    @GetMapping("/denied")
    public ResponseEntity<String> handleDeniedError() {
        String errorMessage = "没有权限访问"; // 设置错误信息

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommunityUtil.getJSONString(403, errorMessage));
    }

}
