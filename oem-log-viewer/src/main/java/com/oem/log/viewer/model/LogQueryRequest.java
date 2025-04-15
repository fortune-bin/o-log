package com.oem.log.viewer.model;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 日志查询请求
 */
@Data
public class LogQueryRequest {
    
    /**
     * 开始时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    
    /**
     * 结束时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
    
    /**
     * 接口路径（模糊匹配）
     */
    private String path;
    
    /**
     * 状态码
     */
    private int statusCode;
    
    /**
     * 最小执行时间（毫秒）
     */
    private long minDuration;
    
    /**
     * 最大执行时间（毫秒）
     */
    private long maxDuration;
    
    /**
     * 错误关键词
     */
    private String errorKeyword;
    
    /**
     * 当前页
     */
    private int page = 1;
    
    /**
     * 每页记录数
     */
    private int pageSize = 20;
} 