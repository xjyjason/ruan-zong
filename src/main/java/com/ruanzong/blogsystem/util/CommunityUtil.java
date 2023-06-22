package com.ruanzong.blogsystem.util;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.apache.commons.codec.EncoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.util.DigestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommunityUtil {

    /**
     * 生成随机字符串
     * @return
     */
    public static String generateUUID() {
        // 去除生成的随机字符串中的 ”-“
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * md5 加密
     * @param key 要加密的字符串
     * @return
     */
    public static String md5(String key) {
        if (StringUtils.isBlank(key)) {
           return null;
        }
        return DigestUtils.md5DigestAsHex(key.getBytes());
    }

    /**
     * 将服务端返回的消息封装成 JSON 格式的字符串
     * @param code 状态码
     * @param msg 提示消息
     * @param map 业务数据
     * @return 返回 JSON 格式字符串
     */
    public static String getJSONString(int code, String msg, Map<String, Object> map) {
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", msg);
        if (map != null) {
            for (String key : map.keySet()) {
                json.put(key, map.get(key));
            }
        }
        return JSONObject.toJSONString(json, SerializerFeature.WriteNullStringAsEmpty, SerializerFeature.WriteMapNullValue);
    }

    // 重载 getJSONString 方法，服务端方法可能不返回业务数据
    public static String getJSONString(int code, String msg) {
        return getJSONString(code, msg, null);
    }

    // 重载 getJSONString 方法，服务端方法可能不返回业务数据和提示消息
    public static String getJSONString(int code) {
        return getJSONString(code, null, null);
    }

    // editor.md 要求返回的 JSON 字符串格式
    public static String getEditorMdJSONString(int success, String message, String url) {
        JSONObject json = new JSONObject();
        json.put("success", success);
        json.put("message", message);
        json.put("url", url);
        return json.toJSONString();
    }

    /**
     * 生成指定位数的数字随机数, 最高不超过 9 位
     *
     * @param length
     * @return
     */
    public static String getRandomCode(int length) {
        Validate.isTrue(length <= 9 && length > 0, "生成数字随机数长度范围应该在 1~9 内, 参数 length : %s", length);
        int floor = (int) Math.pow(10, length - 1);
        int codeNum = RandomUtils.nextInt(floor, floor * 10);
        return Integer.toString(codeNum);
    }


    /**
     * BufferedImage 编码转换为 base64
     * @param bufferedImage
     * @return
     */
    public static String BufferedImageToBase64(BufferedImage bufferedImage) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();//io流
        try {
            ImageIO.write(bufferedImage, "png", baos);//写入流中
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bytes = baos.toByteArray();//转换成字节
        String png_base64 = Base64.encodeBase64String(bytes);//转换成base64串
        png_base64 = png_base64.replaceAll("\n", "").replaceAll("\r", "");//删除 \r\n
        return "data:image/jpg;base64," + png_base64;
    }
}
