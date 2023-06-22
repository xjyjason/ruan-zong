package com.greate.community.controller;

import com.google.code.kaptcha.Producer;
import com.greate.community.entity.User;
import com.greate.community.service.UserService;
import com.greate.community.util.CommunityConstant;
import com.greate.community.util.CommunityUtil;
import com.greate.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
@RequestMapping("/api/login")
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
     * 进入注册界面
     * @return
     */
    @GetMapping("/register")
    public ResponseEntity<String> getRegisterPage() {
        String viewPath = "site/register"; // 设置视图路径

        return ResponseEntity.ok(viewPath);
    }

    /**
     * 进入登录界面
     * @return
     */
    @GetMapping("/login")
    public ResponseEntity<String> getLoginPage() {
        String viewPath = "site/login"; // 设置视图路径

        return ResponseEntity.ok(viewPath);
    }

    /**
     * 进入重置密码界面
     */
    @GetMapping("/resetPwd")
    public ResponseEntity<String> getResetPwdPage() {
        String viewPath = "site/reset-pwd"; // 设置视图路径

        return ResponseEntity.ok(viewPath);
    }

    /**
     * 注册用户
     * @param model
     * @param user
     * @return
     */
   private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public String register(@RequestBody User user) {
        Map<String, Object> map = userService.register(user);
        if (map == null || map.isEmpty()) {
            return CommunityUtil.getJSONString(0, "注册成功，我们已经向您的邮箱发送了一封激活邮件，请尽快激活!");
        } else {
            String usernameMsg = (String) map.get("usernameMsg");
            String passwordMsg = (String) map.get("passwordMsg");
            String emailMsg = (String) map.get("emailMsg");
            return CommunityUtil.getJSONString(1, usernameMsg, passwordMsg, emailMsg);
        }
    }

    /**
     * 激活用户
     * @param model
     * @param userId
     * @param code 激活码
     * @return
     * http://localhost:8080/echo/activation/用户id/激活码
     */

    @GetMapping("/activation/{userId}/{code}")
    public ResponseEntity<Map<String, String>> activation(@PathVariable("userId") int userId,
                                                          @PathVariable("code") String code) {
        Map<String, String> response = new HashMap<>();
        int result = userService.activation(userId, code);
        if (result == ACTIVATION_SUCCESS) {
            response.put("msg", "激活成功，您的账号已经可以正常使用！");
            response.put("target", "/login");
        } else if (result == ACTIVATION_REPEAT) {
            response.put("msg", "无效的操作，您的账号已被激活过！");
            response.put("target", "/index");
        } else {
            response.put("msg", "激活失败，您提供的激活码不正确！");
            response.put("target", "/index");
        }
        return ResponseEntity.ok(response);
    }


    /**
     * 生成验证码, 并存入 Redis
     * @param response
     */
    @GetMapping("/kaptcha")
    public void getKaptcha(HttpServletResponse response) {
        // 生成验证码
        String text = kaptchaProducer.createText(); // 生成随机字符
        System.out.println("验证码：" + text);
        BufferedImage image = kaptchaProducer.createImage(text); // 生成图片
        
        // 验证码的归属者
        String kaptchaOwner = CommunityUtil.generateUUID();
        Cookie cookie = new Cookie("kaptchaOwner", kaptchaOwner);
        cookie.setMaxAge(60);
        cookie.setPath(contextPath);
        response.addCookie(cookie);
        // 将验证码存入 redis
        String redisKey = RedisKeyUtil.getKaptchaKey(kaptchaOwner);
        redisTemplate.opsForValue().set(redisKey, text, 60, TimeUnit.SECONDS);

        // 将图片输出给浏览器
        response.setContentType("image/png");
        try {
            ServletOutputStream os = response.getOutputStream();
            ImageIO.write(image, "png", os);
        } catch (IOException e) {
            logger.error("响应验证码失败", e.getMessage());
        }
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
     * @param username 用户名
     * @param password 密码
     * @param code 验证码
     * @param rememberMe 是否记住我（点击记住我后，凭证的有效期延长）
     * @param model
     * @param kaptchaOwner 从 cookie 中取出的 kaptchaOwner
     * @param response
     * @return
     */
@PostMapping("/api/login")
public String login(@RequestBody Map<String, String> loginData, HttpServletResponse response) {
    String username = loginData.get("username");
    String password = loginData.get("password");
    String code = loginData.get("code");
    boolean rememberMe = Boolean.parseBoolean(loginData.get("rememberMe"));

    // 检查验证码
    String kaptcha = null;
    if (StringUtils.isNotBlank(loginData.get("kaptchaOwner"))) {
        String redisKey = RedisKeyUtil.getKaptchaKey(loginData.get("kaptchaOwner"));
        kaptcha = (String) redisTemplate.opsForValue().get(redisKey);
    }

    if (StringUtils.isBlank(kaptcha) || StringUtils.isBlank(code) || !kaptcha.equalsIgnoreCase(code)) {
        return CommunityUtil.getJSONString(1, "验证码错误");
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
        return CommunityUtil.getJSONString(0, "登录成功");
    } else {
        String usernameMsg = (String) map.get("usernameMsg");
        String passwordMsg = (String) map.get("passwordMsg");
        return CommunityUtil.getJSONString(1, usernameMsg, passwordMsg);
    }
}


    /**
     * 用户登出
     * @param ticket 设置凭证状态为无效
     * @return
     */
@PostMapping("/api/logout")
public String logout(HttpServletRequest request, HttpServletResponse response) {
    String token = request.getHeader("Authorization");
    if (StringUtils.isNotBlank(token)) {
        userService.logout(token);
        // 清除客户端保存的token信息
        response.setHeader("Authorization", "");
        return CommunityUtil.getJSONString(0, "登出成功");
    } else {
        return CommunityUtil.getJSONString(1, "用户未登录");
    }
}

    /**
     * 重置密码
     */
@PostMapping("/api/resetPwd")
@ResponseBody
public Map<String, Object> resetPassword(@RequestBody Map<String, Object> requestData) {
    Map<String, Object> map = new HashMap<>();
    String username = (String) requestData.get("username");
    String password = (String) requestData.get("password");
    String emailVerifyCode = (String) requestData.get("emailVerifyCode");
    String kaptchaCode = (String) requestData.get("kaptchaCode");

    // 检查图片验证码
    String kaptchaCheckRst = checkKaptchaCode(kaptchaOwner, kaptchaCode);
    if (StringUtils.isNotBlank(kaptchaCheckRst)) {
        map.put("status", 1);
        map.put("errMsg", kaptchaCheckRst);
        return map;
    }

    // 检查邮件验证码
    String emailVerifyCodeCheckRst = checkRedisResetPwdEmailCode(username, emailVerifyCode);
    if (StringUtils.isNotBlank(emailVerifyCodeCheckRst)) {
        map.put("status", 1);
        map.put("errMsg", emailVerifyCodeCheckRst);
        return map;
    }

    // 执行重置密码操作
    Map<String, Object> stringObjectMap = userService.doResetPwd(username, password);
    String usernameMsg = (String) stringObjectMap.get("errMsg");
    if (StringUtils.isBlank(usernameMsg)) {
        map.put("status", 0);
        map.put("msg", "重置密码成功!");
        map.put("target", "/login");
    }
    return map;
}


    /**
     * 发送邮件验证码(用于重置密码)
     *
     * @param kaptchaOwner 从 cookie 中取出的 kaptchaOwner
     * @param kaptcha 用户输入的图片验证码
     * @param username 用户输入的需要找回的账号
     */
    @PostMapping("/api/sendEmailCodeForResetPwd")
@ResponseBody
public Map<String, Object> sendEmailCodeForResetPwd(@RequestBody Map<String, Object> requestData) {
    Map<String, Object> map = new HashMap<>();
    String kaptchaOwner = (String) requestData.get("kaptchaOwner");
    String kaptchaCode = (String) requestData.get("kaptchaCode");
    String username = (String) requestData.get("username");

    // 检查图片验证码
    String kaptchaCheckRst = checkKaptchaCode(kaptchaOwner, kaptchaCode);
    if (StringUtils.isNotBlank(kaptchaCheckRst)) {
        map.put("status", 1);
        map.put("errMsg", kaptchaCheckRst);
        return map;
    }

    Map<String, Object> stringObjectMap = userService.doSendEmailCode4ResetPwd(username);
    String usernameMsg = (String) stringObjectMap.get("errMsg");
    if (StringUtils.isBlank(usernameMsg)) {
        map.put("status", 0);
        map.put("msg", "已经往您的邮箱发送了一封验证码邮件，请查收！");
    }
    return map;
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
