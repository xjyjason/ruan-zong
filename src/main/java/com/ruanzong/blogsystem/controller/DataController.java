package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.service.DataService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

/**
 * 网站数据
 */
@RestController
public class DataController implements CommunityConstant {

    @Autowired
    private DataService dataService;

    /**
     * 统计网站 UV
     * @param start
     * @param end
     * @return
     */
    @PostMapping("/data/uv")
    public ResponseEntity<String> getUV(@RequestBody Map<String, Object> time) {
        Date start = new Date((Long)time.get("start"));
        Date end = new Date((Long)time.get("end"));
        long uv = dataService.calculateUV(start, end);
        JSONObject res = new JSONObject();
        res.put("viewer_cnt", uv);
        res.put("start", start.toString());
        res.put("end", end.toString());
        return ResponseEntity.ok(CommunityUtil.getJSONString(HTTP_OK, "操作成功" ,res));
    }

    /**
     * 统计网站 DAU
     * @param start
     * @param end
     * @return
     */
    @PostMapping("/data/dau")
    public ResponseEntity<String> getDAU(@RequestBody JSONObject time) {
        Date start = new Date((Long)time.get("start"));
        Date end = new Date((Long)time.get("end"));
        long dau = dataService.calculateDAU(start, end);
        JSONObject res = new JSONObject();
        res.put("viewer_cnt", dau);
        res.put("start", start.toString());
        res.put("end", end.toString());
        return ResponseEntity.ok(CommunityUtil.getJSONString(HTTP_OK, "操作成功" ,res));
    }

}
