package com.greate.community.controller;

import com.greate.community.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

/**
 * 网站数据
 */
@RestController
public class DataController {

    @Autowired
    private DataService dataService;

    /**
     * 进入统计界面
     * @return
     */
    @GetMapping("/data")
    public ResponseEntity<String> getDataPage() {
        String viewPath = "/site/admin/data"; // 设置视图路径

        return ResponseEntity.ok(viewPath);
    }

    /**
     * 统计网站 UV
     * @param start
     * @param end
     * @return
     */
    @PostMapping("/data/uv")
    public ResponseEntity<Long> getUV(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                                      @DateTimeFormat(pattern = "yyyy-MM-dd") Date end) {
        long uv = dataService.calculateUV(start, end);
        return ResponseEntity.ok(uv);
    }

    /**
     * 统计网站 DAU
     * @param start
     * @param end
     * @return
     */
    @PostMapping("/data/dau")
    public ResponseEntity<Long> getDAU(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                                       @DateTimeFormat(pattern = "yyyy-MM-dd") Date end) {
        long dau = dataService.calculateDAU(start, end);
        return ResponseEntity.ok(dau);
    }

}
