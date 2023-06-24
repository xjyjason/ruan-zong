package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.google.code.kaptcha.Producer;
import com.ruanzong.blogsystem.entity.User;
import com.ruanzong.blogsystem.service.UserService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import com.ruanzong.blogsystem.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录、登出、注册
 */
@RestController
@RequestMapping("/api/status")
public class LoginController implements CommunityConstant {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private Producer kaptchaProducer;

    @Autowired
    private RedisTemplate redisTemplate;

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 注册用户
     * @param user
     * @return
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User user) {
        Map<String, Object> err = userService.register(user);
        if (err == null || err.isEmpty()) {
            return ResponseEntity.ok().body(CommunityUtil.getJSONString(200, "注册成功, 我们已经向您的邮箱发送了一封激活邮件，请尽快激活!"));
        } else {
            return ResponseEntity.badRequest().body(CommunityUtil.getJSONString(400, "注册失败", err));
        }
    }

    /**
     * 激活用户
     * @param userId
     * @param code 激活码
     * @return
     * http://localhost:8080/echo/activation/用户id/激活码
     */

    @GetMapping("/activation/{userId}/{code}")
    public ResponseEntity<String> activation(@PathVariable("userId") int userId,
                                                          @PathVariable("code") String code) {
        int result = userService.activation(userId, code);
        if (result == ACTIVATION_SUCCESS) {
            return ResponseEntity.ok(CommunityUtil.getJSONString(200,"激活成功，您的账号已经可以正常使用！"));
        } else if (result == ACTIVATION_REPEAT) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "无效的操作，您的账号已被激活过！"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "激活失败，您提供的激活码不正确！"));
        }
    }


    /**
     * 生成验证码, 并存入 Redis
     */
    @GetMapping("/kaptcha")
    public ResponseEntity<String> getKaptcha(HttpServletResponse response) {
        JSONObject res = new JSONObject();
        // 生成验证码
        String text = kaptchaProducer.createText(); // 生成随机字符
        BufferedImage image = kaptchaProducer.createImage(text); // 生成图片
        
        // 验证码的归属者
        String kaptchaOwner = CommunityUtil.generateUUID();
        Cookie cookie = new Cookie("kaptchaOwner", kaptchaOwner);
        cookie.setMaxAge(180);
        cookie.setPath(contextPath);
        response.addCookie(cookie);
        // 将验证码存入 redis
        String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
        redisTemplate.opsForValue().set(redisKey, text, 180, TimeUnit.SECONDS);

        // 将图片输出
        res.put("image", CommunityUtil.BufferedImageToBase64(image));
        return ResponseEntity.ok(CommunityUtil.getJSONString(200, "图片成功获取", res));
    }

    /**
     * 验证用户输入的图片验证码是否和redis中存入的是否相等
     *
     * @param kaptchaOwner 从 cookie 中取出的 kaptchaOwner
     * @param checkCode 用户输入的图片验证码
     * @return 失败则返回原因, 验证成功返回 "",
     */
    private String checkKaptchaCode(String kaptchaOwner, String checkCode) {
        if (StringUtils.isBlank(checkCode)) {
            return "未发现输入的图片验证码";
        }
        String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
        String kaptchaValue = (String) redisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isBlank(kaptchaValue)) {
            return "图片验证码过期";
        } else if (!kaptchaValue.equalsIgnoreCase(checkCode)) {
            return "图片验证码错误";
        }
        return "";
    }

    /**
     * 用户登录
     * @param loginData
     * @param response
     * @return
     */
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Map<String, String> loginData, HttpServletResponse response) {
        String username = loginData.get("username");
        String password = loginData.get("password");
        String code = loginData.get("code");
        boolean rememberMe = Boolean.parseBoolean(loginData.get("rememberMe"));

        // 检查验证码
        String kaptcha = null;
        if (StringUtils.isNotBlank(loginData.get("kaptchaOwner"))) {
            String redisKey = RedisKeyUtil.getKaptchaKey(loginData.get("kaptchaOwner"));
            kaptcha = (String) redisTemplate.opsForValue().get(redisKey);
            System.out.println(kaptcha);
        }

        if (StringUtils.isBlank(kaptcha) || StringUtils.isBlank(code) || !kaptcha.equalsIgnoreCase(code)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, "验证码错误"));
        }

        // 凭证过期时间（是否记住我）
        int expiredSeconds = rememberMe ? REMEMBER_EXPIRED_SECONDS : DEFAULT_EXPIRED_SECONDS;
        // 验证用户名和密码
        Map<String, Object> map = userService.login(username, password, expiredSeconds);
        if (map.containsKey("ticket")) {
            // 账号和密码均正确，则服务端会生成 ticket，浏览器通过 cookie 存储 ticket
            String ticket = map.get("ticket").toString();
            Cookie cookie = new Cookie("ticket", ticket);
            cookie.setPath(contextPath); // cookie 有效范围
            cookie.setMaxAge(expiredSeconds);
            response.addCookie(cookie);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(CommunityUtil.getJSONString(200, "登录成功"));
        } else {
            String usernameMsg = (String) map.get("usernameMsg");
            String passwordMsg = (String) map.get("passwordMsg");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CommunityUtil.getJSONString(400, usernameMsg == null ? passwordMsg: passwordMsg));
        }
    }

    /**
     * 重置密码
     */
    @PostMapping("/reset-password-verify")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, Object> requestData) {
        String username = (String) requestData.get("username");
        String password = (String) requestData.get("password");
        String emailVerifyCode = (String) requestData.get("emailVerifyCode");

        // 检查邮件验证码
        String emailVerifyCodeCheckRst = checkRedisResetPwdEmailCode(username, emailVerifyCode);
        if (StringUtils.isNotBlank(emailVerifyCodeCheckRst)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, emailVerifyCodeCheckRst));
        }

        // 执行重置密码操作
        Map<String, Object> stringObjectMap = userService.doResetPwd(username, password);
        String usernameMsg = (String) stringObjectMap.get("errMsg");
        if (StringUtils.isBlank(usernameMsg)) {
            return  ResponseEntity.ok(CommunityUtil.getJSONString(200, "重置密码成功！"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "重置密码失败"));
    }


    /**
     * 发送邮件验证码(用于重置密码)
     *
     * @param requestData
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> sendEmailCodeForResetPwd(@RequestBody Map<String, Object> requestData) {
        Map<String, Object> map = new HashMap<>();
        String kaptchaOwner = (String) requestData.get("kaptchaOwner");
        String kaptchaCode = (String) requestData.get("kaptchaCode");
        String username = (String) requestData.get("username");

        // 检查图片验证码
        String kaptchaCheckRst = checkKaptchaCode(kaptchaOwner, kaptchaCode);
        if (StringUtils.isNotBlank(kaptchaCheckRst)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, kaptchaCheckRst));
        }

        Map<String, Object> stringObjectMap = userService.doSendEmailCode4ResetPwd(username);
        String usernameMsg = (String) stringObjectMap.get("errMsg");
        if (StringUtils.isBlank(usernameMsg)) {
            return ResponseEntity.status(HttpStatus.OK).body(CommunityUtil.getJSONString(200, "已经往您的邮箱发送了一封验证码邮件, 请查收!"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "重置密码请求失败"));
    }


    /**
     * 检查 邮件 验证码
     *
     * @param username 用户名
     * @param checkCode 用户输入的图片验证码
     * @return 验证成功 返回"", 失败则返回原因
     */
    private String checkRedisResetPwdEmailCode(String username, String checkCode) {
        if (StringUtils.isBlank(checkCode)) {
            return "未发现输入的邮件验证码";
        }
        final String redisKey = "EmailCode4ResetPwd:" + username;
        String emailVerifyCodeInRedis = (String) redisTemplate.opsForValue().get(redisKey);
        if (StringUtils.isBlank(emailVerifyCodeInRedis)) {
            return "邮件验证码已过期";
        } else if (!emailVerifyCodeInRedis.equalsIgnoreCase(checkCode)) {
            return "邮件验证码错误";
        }
        return "";
    }


}
