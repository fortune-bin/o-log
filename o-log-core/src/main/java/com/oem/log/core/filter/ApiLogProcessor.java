package com.oem.log.core.filter;

import com.oem.log.core.model.ApiLog;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.http.HttpServletRequest;

/**
 * API日志处理器接口
 * 用于SPI扩展，第三方可实现此接口提供自定义处理逻辑
 */
public interface ApiLogProcessor {
    
    /**
     * 处理请求
     * @param request 包装后的请求对象
     * @param logBuilder 日志构建器
     */
    void processRequest(ContentCachingRequestWrapper request, ApiLog.ApiLogBuilder logBuilder);
    
    /**
     * 处理响应
     * @param response 包装后的响应对象
     * @param logBuilder 日志构建器
     */
    void processResponse(ContentCachingResponseWrapper response, ApiLog.ApiLogBuilder logBuilder);
    
    /**
     * 处理异常
     * @param exception 捕获的异常
     * @param logBuilder 日志构建器
     */
    void processException(Exception exception, ApiLog.ApiLogBuilder logBuilder);
    
    /**
     * 判断是否应处理此请求
     * @param request HTTP请求
     * @return 如果应该处理返回true，否则返回false
     */
    boolean shouldProcess(HttpServletRequest request);
} 