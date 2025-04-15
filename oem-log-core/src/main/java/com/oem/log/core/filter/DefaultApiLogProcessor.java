package com.oem.log.core.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oem.log.core.model.ApiLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 默认的API日志处理器实现
 */
@Slf4j
public class DefaultApiLogProcessor implements ApiLogProcessor {

    private final ObjectMapper objectMapper;
    private final int maxContentLength;
    
    // 不记录二进制内容的媒体类型
    private static final List<String> INVISIBLE_CONTENT_TYPES = Arrays.asList(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE, 
            MediaType.IMAGE_GIF_VALUE,
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            MediaType.APPLICATION_PDF_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE
    );

    public DefaultApiLogProcessor(ObjectMapper objectMapper, int maxContentLength) {
        this.objectMapper = objectMapper;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public void processRequest(ContentCachingRequestWrapper request, ApiLog.ApiLogBuilder logBuilder) {
        try {
            // 记录查询参数
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                logBuilder.requestParams("Query: " + queryString);
            }
            
            // 记录请求体
            if (shouldCaptureContent(request.getContentType()) && request.getContentLength() > 0) {
                byte[] content = request.getContentAsByteArray();
                if (content.length > 0) {
                    String contentAsString = new String(content, StandardCharsets.UTF_8);
                    // 截断过长内容
                    if (contentAsString.length() > maxContentLength) {
                        contentAsString = contentAsString.substring(0, maxContentLength) + "... (content truncated)";
                    }
                    logBuilder.requestParams(contentAsString);
                }
            }
            
            // 可选：记录请求头
            // logBuilder.requestHeaders(getHeadersAsString(request));
        } catch (Exception e) {
            log.warn("处理请求内容异常", e);
        }
    }

    @Override
    public void processResponse(ContentCachingResponseWrapper response, ApiLog.ApiLogBuilder logBuilder) {
        try {
            // 记录响应体
            if (shouldCaptureContent(response.getContentType()) && response.getContentSize() > 0) {
                byte[] content = response.getContentAsByteArray();
                if (content.length > 0) {
                    String contentAsString = new String(content, StandardCharsets.UTF_8);
                    // 截断过长内容
                    if (contentAsString.length() > maxContentLength) {
                        contentAsString = contentAsString.substring(0, maxContentLength) + "... (content truncated)";
                    }
                    logBuilder.responseBody(contentAsString);
                }
            }
        } catch (Exception e) {
            log.warn("处理响应内容异常", e);
        }
    }

    @Override
    public void processException(Exception exception, ApiLog.ApiLogBuilder logBuilder) {
        // 记录异常信息
        logBuilder.exceptionMsg(exception.getMessage());
        
        // 可选：记录异常堆栈
        /*
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        // 限制堆栈长度
        if (stackTrace.length() > maxContentLength) {
            stackTrace = stackTrace.substring(0, maxContentLength) + "... (stack trace truncated)";
        }
        logBuilder.exceptionMsg(stackTrace);
        */
    }

    @Override
    public boolean shouldProcess(HttpServletRequest request) {
        // 默认处理所有请求
        return true;
    }
    
    /**
     * 判断是否应该捕获内容
     */
    private boolean shouldCaptureContent(String contentType) {
        if (contentType == null) {
            return true;
        }
        
        return !INVISIBLE_CONTENT_TYPES.stream()
                .anyMatch(type -> contentType.toLowerCase().contains(type.toLowerCase()));
    }
} 