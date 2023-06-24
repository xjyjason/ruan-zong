package com.ruanzong.blogsystem.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruanzong.blogsystem.service.DataService;
import com.ruanzong.blogsystem.util.CommunityConstant;
import com.ruanzong.blogsystem.util.CommunityUtil;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

/**
 * 网站数据
 */
@RestController
@RequestMapping("/api/data")
public class DataController implements CommunityConstant {

    @Autowired
    private DataService dataService;

    /**
     * 统计网站 UV
     * @param start
     * @param end
     * @return
     */
    @PostMapping("/uv")
    public ResponseEntity<String> getUV(@RequestBody Map<String, Object> time) {
        Date start = new Date(Long.parseLong(time.get("start").toString()));
        Date end = new Date(Long.parseLong(time.get("end").toString()));
        if(start.after(end)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "请保证起始时间在结束时间之前"));
        }
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
    @PostMapping("/dau")
    public ResponseEntity<String> getDAU(@RequestBody JSONObject time) {
        Date start = new Date(Long.parseLong(time.get("start").toString()));
        Date end = new Date(Long.parseLong(time.get("end").toString()));
        if(start.after(end)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommunityUtil.getJSONString(400, "请保证起始时间在结束时间之前"));
        }
        long dau = dataService.calculateDAU(start, end);
        JSONObject res = new JSONObject();
        res.put("viewer_cnt", dau);
        res.put("start", start.toString());
        res.put("end", end.toString());
        return ResponseEntity.ok(CommunityUtil.getJSONString(HTTP_OK, "操作成功" ,res));
    }

}
