package com.greate.community.controller;

import com.greate.community.entity.Comment;
import com.greate.community.entity.DiscussPost;
import com.greate.community.entity.Page;
import com.greate.community.entity.User;
import com.greate.community.service.*;
import com.greate.community.util.CommunityConstant;
import com.greate.community.util.CommunityUtil;
import com.greate.community.util.HostHolder;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public ResponseEntity<Map<String, Object>> updateHeaderUrl(@RequestParam("fileName") String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", "文件名不能为空"));
        }

        // Update header URL in user profile
        String url = headerBucketUrl + "/" + fileName;
        userService.updateHeader(hostHolder.getUser().getId(), url);

        return ResponseEntity.ok(Collections.emptyMap());
    }

    /**
     * 修改用户密码
     * @param oldPassword 原密码
     * @param newPassword 新密码
     * @return
     */
    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> updatePassword(
            @RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword
    ) {
        // Validate old password
        User user = hostHolder.getUser();
        String md5OldPassword = CommunityUtil.md5(oldPassword + user.getSalt());
        if (!user.getPassword().equals(md5OldPassword)) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", "原密码错误"));
        }

        // Validate new password
        String md5NewPassword = CommunityUtil.md5(newPassword + user.getSalt());
        if (user.getPassword().equals(md5NewPassword)) {
            return ResponseEntity.badRequest()
                    .body(Collections.singletonMap("message", "新密码和原密码相同"));
        }

        // Update user password
        userService.updatePassword(user.getId(), newPassword);

        return ResponseEntity.ok(Collections.emptyMap());
    }

    /**
     * 进入个人主页
     * @param userId 可以进入任意用户的个人主页
     * @return
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<Map<String, Object>> getProfilePage(@PathVariable("userId") int userId) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("userLikeCount", likeService.findUserLikeCount(userId));
        response.put("followeeCount", followService.findFolloweeCount(userId, ENTITY_TYPE_USER));
        response.put("followerCount", followService.findFollowerCount(ENTITY_TYPE_USER, userId));
        response.put("hasFollowed", hostHolder.getUser() != null && followService.hasFollowed(hostHolder.getUser().getId(), ENTITY_TYPE_USER, userId));
        response.put("tab", "profile");

        return ResponseEntity.ok(response);
    }

    /**
     * 进入我的帖子（查询某个用户的帖子列表）
     * @param userId
     * @param page
     * @return
     */
    @GetMapping("/discuss/{userId}")
    public ResponseEntity<Map<String, Object>> getMyDiscussPosts(
            @PathVariable("userId") int userId,
            Page page
    ) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);

        int rows = discussPostService.findDiscussPostRows(userId);
        response.put("rows", rows);

        page.setLimit(5);
        page.setPath("/user/discuss/" + userId);
        page.setRows(rows);

        List<DiscussPost> list = discussPostService.findDiscussPosts(userId, page.getOffset(), page.getLimit(), 0);
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if (list != null) {
            for (DiscussPost post : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", post);
                map.put("likeCount", likeService.findEntityLikeCount(ENTITY_TYPE_POST, post.getId()));
                discussPosts.add(map);
            }
        }
        response.put("discussPosts", discussPosts);
        response.put("tab", "mypost");

        return ResponseEntity.ok(response);
    }

    /**
     * 进入我的评论/回复（查询某个用户的评论/回复列表）
     * @param userId
     * @param page
     * @return
     */
    @GetMapping("/comment/{userId}")
    public ResponseEntity<Map<String, Object>> getMyComments(
            @PathVariable("userId") int userId,
            Page page
    ) {
        User user = userService.findUserById(userId);
        if (user == null) {
            throw new RuntimeException("该用户不存在");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("user", user);

        int commentCounts = commentService.findCommentCountByUserId(userId);
        response.put("commentCounts", commentCounts);

        page.setLimit(5);
        page.setPath("/user/comment/" + userId);
        page.setRows(commentCounts);

        List<Comment> list = commentService.findCommentByUserId(userId, page.getOffset(), page.getLimit());
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
        }
        response.put("comments", comments);
        response.put("tab", "myreply");

        return ResponseEntity.ok(response);
    }
}
