package com.oem.log.core.filter.example;

import com.oem.log.core.filter.ApiLogProcessor;
import com.oem.log.core.model.ApiLog;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.http.HttpServletRequest;

/**
 * 示例：自定义路径处理器
 * 仅处理特定路径的请求
 */
public class ApiPathLogProcessor implements ApiLogProcessor {

    private final String[] pathPrefixes;
    
    /**
     * 构造函数
     * @param pathPrefixes 要监控的路径前缀
     */
    public ApiPathLogProcessor(String... pathPrefixes) {
        this.pathPrefixes = pathPrefixes;
    }
    
    @Override
    public void processRequest(ContentCachingRequestWrapper request, ApiLog.ApiLogBuilder logBuilder) {
        // 记录用户信息（可从请求头或会话中获取）
        String userId = request.getHeader("X-User-ID");
        if (userId != null && !userId.isEmpty()) {
            String existingParams = logBuilder.build().getRequestParams();
            String userInfo = "UserID: " + userId;
            
            if (existingParams != null && !existingParams.isEmpty()) {
                logBuilder.requestParams(existingParams + ", " + userInfo);
            } else {
                logBuilder.requestParams(userInfo);
            }
        }
    }

    @Override
    public void processResponse(ContentCachingResponseWrapper response, ApiLog.ApiLogBuilder logBuilder) {
        // 此示例中不做额外处理
    }

    @Override
    public void processException(Exception exception, ApiLog.ApiLogBuilder logBuilder) {
        // 可以添加自定义异常处理逻辑
        if (exception instanceof IllegalArgumentException) {
            logBuilder.exceptionMsg("参数错误: " + exception.getMessage());
        }
    }

    @Override
    public boolean shouldProcess(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // 检查是否匹配路径前缀
        if (pathPrefixes != null && pathPrefixes.length > 0) {
            for (String prefix : pathPrefixes) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
        
        // 默认处理所有请求
        return true;
    }
} 