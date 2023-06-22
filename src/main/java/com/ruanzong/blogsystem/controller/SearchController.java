package com.greate.community.controller;

import com.greate.community.entity.DiscussPost;
import com.greate.community.entity.Page;
import com.greate.community.service.DiscussPostService;
import com.greate.community.service.ElasticsearchService;
import com.greate.community.service.LikeService;
import com.greate.community.service.UserService;
import com.greate.community.util.CommunityConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

/**
 * 搜索
 */
@RestController
@RequestMapping("/api/search")
public class SearchApiController implements CommunityConstant {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private UserService userService;

    @Autowired
    private LikeService likeService;

    /**
     * 搜索
     * GET /api/search?keyword=xxx&page=1&limit=10
     * @param keyword 关键词
     * @param page    分页信息
     * @return 搜索结果
     */
    @GetMapping
    public Map<String, Object> search(String keyword, Page page) {
        Map<String, Object> result = new HashMap<>();

        // 搜索帖子
        org.springframework.data.domain.Page<DiscussPost> searchResult =
                elasticsearchService.searchDiscussPost(keyword, page.getCurrent() - 1, page.getLimit());
        // 聚合数据
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if (searchResult != null) {
            for (DiscussPost post : searchResult) {
                Map<String, Object> map = new HashMap<>();
                // 帖子
                map.put("post", post);
                // 作者
                map.put("user", userService.findUserById(post.getUserId()));
                // 点赞数量
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));

                discussPosts.add(map);
            }
        }

        result.put("discussPosts", discussPosts);
        result.put("keyword", keyword);

        // 设置分页
        page.setPath("/api/search");
        page.setRows(searchResult == null ? 0 : (int) searchResult.getTotalElements());
        result.put("page", page);

        return result;
    }
}
