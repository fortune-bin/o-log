package com.oem.log.viewer.controller;

import com.oem.log.viewer.model.LogQueryRequest;
import com.oem.log.viewer.model.LogQueryResult;
import com.oem.log.viewer.service.LogQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Calendar;
import java.util.Date;

/**
 * 日志查看控制器
 */
@Controller
public class LogViewController {
    
    @Autowired
    private LogQueryService logQueryService;
    
    /**
     * 日志查询页面
     */
    @GetMapping("/")
    public String index(Model model) {
        // 默认查询最近1小时的数据
        LogQueryRequest request = new LogQueryRequest();
        
        Calendar calendar = Calendar.getInstance();
        request.setEndTime(calendar.getTime());
        
        calendar.add(Calendar.HOUR, -1);
        request.setStartTime(calendar.getTime());
        
        model.addAttribute("request", request);
        return "index";
    }
    
    /**
     * 查询日志API
     */
    @PostMapping("/api/logs")
    @ResponseBody
    public LogQueryResult queryLogs(@ModelAttribute LogQueryRequest request) {
        return logQueryService.queryLogs(request);
    }
} 