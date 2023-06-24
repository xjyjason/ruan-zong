package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.entity.Message;
import com.ruanzong.blogsystem.entity.User;
import com.ruanzong.blogsystem.service.MessageService;
import com.ruanzong.blogsystem.service.UserService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.util.*;

/**
 * 私信/系统通知
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController implements CommunityConstant {

    @Autowired
    private HostHolder hostHolder;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserService userService;

    /**
     * 私信列表
     * @param page
     * @return
     */
    @GetMapping("/letter/list")
    @ResponseBody
    public ResponseEntity<String> getLetterList(@RequestParam("page") int page) {
        Map<String, Object> map = new HashMap<>();
        // 获取当前登录用户信息
        User user = hostHolder.getUser();
        // 分页信息
        int offset = (page - 1) * PageLimit;
        int totalCount = messageService.findConversationCout(user.getId());
        int totalPages = (int) Math.ceil((double) totalCount / PageLimit);

        // 私信列表
        List<Message> conversationList = messageService.findConversations(
                user.getId(), offset, PageLimit);

        List<Map<String, Object>> conversations = new ArrayList<>();
        if (conversationList != null) {
            for (Message message : conversationList) {
                Map<String, Object> conversationMap = new HashMap<>();
                conversationMap.put("conversation", message); // 私信
                conversationMap.put("letterCount", messageService.findLetterCount(
                        message.getConversationId())); // 私信数量
                conversationMap.put("unreadCount", messageService.findLetterUnreadCount(
                        user.getId(), message.getConversationId())); // 未读私信数量
                int targetId = user.getId() == message.getFromId() ? message.getToId() : message.getFromId();
                conversationMap.put("target", userService.findUserById(targetId)); // 私信对方

                conversations.add(conversationMap);
            }
        }

        map.put("conversations", conversations);
        map.put("page_cnt", totalPages);
        map.put("page_current", page);

        // 查询当前用户的所有未读消息数量
        int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
        map.put("letterUnreadCount", letterUnreadCount);
        int noticeUnreadCount = messageService.findNoticeUnReadCount(user.getId(), null);
        map.put("noticeUnreadCount", noticeUnreadCount);

        return ResponseEntity.ok(CommunityUtil.getJSONString(200, "获取成功", map));
    }

    /**
     * 私信详情页
     * @param conversationId
     * @param page
     * @return
     */
    @GetMapping("/letter/detail/{conversationId}")
    public ResponseEntity<String> getLetterDetail(@PathVariable("conversationId") String conversationId, @RequestParam("page") int page) {
        Map<String, Object> map = new HashMap<>();
        // 分页信息
        int offset = (page - 1) * PageLimit;
        int totalCount = messageService.findLetterCount(conversationId);
        int totalPages = (int) Math.ceil((double) totalCount / PageLimit);

        // 私信列表
        List<Message> letterList = messageService.findLetters(conversationId, offset, PageLimit);

        List<Map<String, Object>> letters = new ArrayList<>();
        if (letterList != null) {
            for (Message message : letterList) {
                Map<String, Object> letterMap = new HashMap<>();
                letterMap.put("letter", message);
                letterMap.put("fromUser", userService.findUserById(message.getFromId()));
                letters.add(letterMap);
            }
        }

        map.put("letters", letters);
        map.put("totalPages", totalPages);

        // 私信目标
        map.put("target", getLetterTarget(conversationId));

        // 将私信列表中的未读消息改为已读
        List<Integer> ids = getUnreadLetterIds(letterList);
        if (!ids.isEmpty()) {
            messageService.readMessage(ids);
        }

        return ResponseEntity.ok(CommunityUtil.getJSONString(200,"获取成功", map));
    }

    /**
     * 获取私信对方对象
     * @param conversationId
     * @return
     */
    private User getLetterTarget(String conversationId) {
        String[] ids = conversationId.split("_");
        int id0 = Integer.parseInt(ids[0]);
        int id1 = Integer.parseInt(ids[1]);

        if (hostHolder.getUser().getId() == id0) {
            return userService.findUserById(id1);
        }
        else {
            return userService.findUserById(id0);
        }
    }

    /**
     * 获取当前登录用户未读私信的 id
     * @param letterList
     * @return
     */
    private List<Integer> getUnreadLetterIds(List<Message> letterList) {
        List<Integer> ids = new ArrayList<>();

        if (letterList != null) {
            for (Message message : letterList) {
                // 当前用户是私信的接收者且该私信处于未读状态
                if (hostHolder.getUser().getId() == message.getToId() && message.getStatus() == 0) {
                    ids.add(message.getId());
                }
            }
        }

        return ids;
    }

    /**
     * 发送私信
     * @param toName 收信人 username
     * @param content 内容
     * @return
     */
    @PostMapping("/letter/send")
    @ResponseBody
    public ResponseEntity<String> sendLetter(String toName, String content) {
        // Integer.valueOf("abc"); // 测试统一异常处理（异步请求）
        User target = userService.findUserByName(toName);
        if (target == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommunityUtil.getJSONString(404, "目标用户不存在"));
        }
        Message message = new Message();
        message.setFromId(hostHolder.getUser().getId());
        message.setToId(target.getId());
        if (message.getFromId() < message.getToId()) {
            message.setConversationId(message.getFromId() + "_" + message.getToId());
        }
        else {
            message.setConversationId(message.getToId() + "_" + message.getFromId());
        }
        message.setContent(content);
        message.setStatus(0); // 默认就是 0 未读，可不写
        message.setCreateTime(new Date());

        messageService.addMessage(message);

        return ResponseEntity.ok().body(CommunityUtil.getJSONString(200, "发送成功"));
    }

    /**
     * 通知列表（只显示最新一条消息）
     * @return
     */
    @GetMapping("/notice/list")
    public ResponseEntity<String> getNoticeList() {
        User user = hostHolder.getUser();
        Map<String, Object> result = new HashMap<>();

        // 查询评论类通知
        Message commentMessage = messageService.findLatestNotice(user.getId(), TOPIC_COMMNET);
        Map<String, Object> commentNotice = convertMessageToNotice(commentMessage, TOPIC_COMMNET);
        result.put("commentNotice", commentNotice);

        // 查询点赞类通知
        Message likeMessage = messageService.findLatestNotice(user.getId(), TOPIC_LIKE);
        Map<String, Object> likeNotice = convertMessageToNotice(likeMessage, TOPIC_LIKE);
        result.put("likeNotice", likeNotice);

        // 查询关注类通知
        Message followMessage = messageService.findLatestNotice(user.getId(), TOPIC_FOLLOW);
        Map<String, Object> followNotice = convertMessageToNotice(followMessage, TOPIC_FOLLOW);
        result.put("followNotice", followNotice);

        // 查询未读消息数量
        int letterUnreadCount = messageService.findLetterUnreadCount(user.getId(), null);
        int noticeUnreadCount = messageService.findNoticeUnReadCount(user.getId(), null);

        result.put("letterUnreadCount", letterUnreadCount);
        result.put("noticeUnreadCount", noticeUnreadCount);

        return ResponseEntity.ok(CommunityUtil.getJSONString(200, "获取成功", result));
    }

    private Map<String, Object> convertMessageToNotice(Message message, String topic) {
        Map<String, Object> notice = new HashMap<>();
        if (message != null) {
            notice.put("message", message);

            String content = HtmlUtils.htmlUnescape(message.getContent());
            Map<String, Object> data = JSONObject.parseObject(content, HashMap.class);

            User user = userService.findUserById((Integer) data.get("userId"));
            notice.put("user", userService.findUserById((Integer) data.get("userId")));
            notice.put("entityType", data.get("entityType"));
            notice.put("entityId", data.get("entityId"));
            notice.put("postId", data.get("postId"));

            int count = messageService.findNoticeCount(user.getId(), topic);
            notice.put("count", count);

            int unread = messageService.findNoticeUnReadCount(user.getId(), topic);
            notice.put("unread", unread);
        }
        return notice;
    }


    /**
     * 查询某个主题所包含的通知列表
     * @param topic
     * @param page
     * @return
     */
    @GetMapping("/notice/detail/{topic}/{page}")
    public ResponseEntity<String> getNoticeDetail(@PathVariable("topic") String topic, int page) {
        User user = hostHolder.getUser();
        Map<String, Object> result = new HashMap<>();

        int pageCount = messageService.findNoticeCount(user.getId(), topic);
        int offset = (page-1) / PageLimit;

        List<Message> noticeList = messageService.findNotices(user.getId(), topic, offset, PageLimit);
        List<Map<String, Object>> noticeVoList = new ArrayList<>();
        if (noticeList != null) {
            for (Message notice : noticeList) {
                Map<String, Object> noticeVo = convertMessageToNotice(notice, topic);
                noticeVo.put("fromUser", userService.findUserById(notice.getFromId()));
                noticeVoList.add(noticeVo);
            }
        }
        result.put("notices", noticeVoList);

        // 设置已读
        List<Integer> ids = getUnreadLetterIds(noticeList);
        if (!ids.isEmpty()) {
            messageService.readMessage(ids);
        }
        result.put("page_cnt", pageCount);
        result.put("page_current", page);

        return ResponseEntity.ok(CommunityUtil.getJSONString(200, "获取成功", result));
    }

}
