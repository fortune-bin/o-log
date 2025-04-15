package com.oem.log.core.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API调用日志记录
 */
@Data
@Builder
public class ApiLog implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 唯一ID（UUID）
     */
    private String id;
    
    /**
     * 主机名
     */
    private String hostname;
    
    /**
     * 请求时间
     */
    private LocalDateTime requestTime;
    
    /**
     * 请求路径
     */
    private String path;
    
    /**
     * 请求方法（GET/POST等）
     */
    private String method;
    
    /**
     * 请求参数
     */
    private String requestParams;
    
    /**
     * 请求头信息（选择性记录）
     */
    private String requestHeaders;
    
    /**
     * 请求IP
     */
    private String clientIp;
    
    /**
     * 响应状态码
     */
    private int statusCode;
    
    /**
     * 响应数据
     */
    private String responseBody;
    
    /**
     * 异常信息（如果有）
     */
    private String exceptionMsg;
    
    /**
     * 执行耗时（毫秒）
     */
    private long executionTime;
} 