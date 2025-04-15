package com.oem.log.core.filter;

import com.oem.log.core.model.ApiLog;
import com.oem.log.core.store.LogStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;

/**
 * API日志记录过滤器
 * 替代原有AOP实现，提供更高性能和灵活性
 */
@Slf4j
public class ApiLogFilter extends OncePerRequestFilter {

    private final LogStore logStore;
    private final List<String> urlPatterns;
    private final List<ApiLogProcessor> processors = new ArrayList<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public ApiLogFilter(LogStore logStore, List<String> urlPatterns) {
        this.logStore = logStore;
        this.urlPatterns = urlPatterns;
        loadProcessors();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = urlPathHelper.getPathWithinApplication(request);
        
        // 检查是否匹配URL模式
        if (!isUrlMatch(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 检查自定义处理器是否认为应该处理此请求
        boolean shouldProcess = false;
        for (ApiLogProcessor processor : processors) {
            if (processor.shouldProcess(request)) {
                shouldProcess = true;
                break;
            }
        }
        
        // 如果没有配置处理器且URL匹配，默认处理
        if (!shouldProcess && processors.size() > 0) {
            filterChain.doFilter(request, response);
            return;
        }

        // 包装请求和响应以捕获内容
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
            log.warn("获取主机名失败", e);
        }

        ApiLog.ApiLogBuilder logBuilder = ApiLog.builder()
                .id(UUID.randomUUID().toString())
                .hostname(hostname)
                .requestTime(LocalDateTime.now())
                .path(path)
                .method(request.getMethod())
                .clientIp(getClientIp(request));

        Exception exception = null;

        try {
            // 处理请求阶段
            for (ApiLogProcessor processor : processors) {
                if (processor.shouldProcess(request)) {
                    try {
                        processor.processRequest(requestWrapper, logBuilder);
                    } catch (Exception e) {
                        log.warn("处理请求日志时出错", e);
                    }
                }
            }

            // 继续过滤器链
            filterChain.doFilter(requestWrapper, responseWrapper);

            // 设置状态码
            logBuilder.statusCode(responseWrapper.getStatus());

            // 处理响应阶段
            for (ApiLogProcessor processor : processors) {
                if (processor.shouldProcess(request)) {
                    try {
                        processor.processResponse(responseWrapper, logBuilder);
                    } catch (Exception e) {
                        log.warn("处理响应日志时出错", e);
                    }
                }
            }

        } catch (Exception e) {
            exception = e;
            logBuilder.statusCode(500);
            logBuilder.exceptionMsg(e.getMessage());

            // 处理异常阶段
            for (ApiLogProcessor processor : processors) {
                if (processor.shouldProcess(request)) {
                    try {
                        processor.processException(e, logBuilder);
                    } catch (Exception ex) {
                        log.warn("处理异常日志时出错", ex);
                    }
                }
            }

            if (e instanceof ServletException) {
                throw (ServletException) e;
            } else if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new ServletException(e);
            }
        } finally {
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            logBuilder.executionTime(executionTime);

            // 保存日志
            try {
                logStore.store(logBuilder.build());
            } catch (Exception e) {
                log.error("保存API日志失败", e);
            }

            // 确保响应内容被复制到原始响应
            try {
                responseWrapper.copyBodyToResponse();
            } catch (IOException e) {
                log.error("复制响应内容失败", e);
            }
        }
    }

    /**
     * 检查请求URL是否匹配配置的模式
     */
    private boolean isUrlMatch(String path) {
        if (urlPatterns == null || urlPatterns.isEmpty()) {
            return true; // 默认监控所有请求
        }

        for (String pattern : urlPatterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用SPI加载处理器
     */
    private void loadProcessors() {
        // 加载SPI定义的处理器
        ServiceLoader<ApiLogProcessor> serviceLoader = ServiceLoader.load(ApiLogProcessor.class);
        for (ApiLogProcessor processor : serviceLoader) {
            processors.add(processor);
            log.info("加载API日志处理器: {}", processor.getClass().getName());
        }
    }

    /**
     * 添加处理器(程序化配置)
     */
    public void addProcessor(ApiLogProcessor processor) {
        processors.add(processor);
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
} 