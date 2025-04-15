package com.oem.log.core.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oem.log.core.annotation.ApiMonitor;
import com.oem.log.core.model.ApiLog;
import com.oem.log.core.store.LogStore;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API日志记录切面
 */
@Aspect
@Component
@Slf4j
public class ApiLogAspect {
    
    @Autowired
    private LogStore logStore;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 定义切点：所有标注了@ApiMonitor注解的方法
     */
    @Pointcut("@annotation(com.oem.log.core.annotation.ApiMonitor)")
    public void apiLogPointcut() {
    }
    
    /**
     * 环绕通知：记录请求和响应
     */
    @Around("apiLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes == null) {
            // 非Web环境，直接执行
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        String hostname = InetAddress.getLocalHost().getHostName();
        ApiLog.ApiLogBuilder logBuilder = ApiLog.builder()
                .id(UUID.randomUUID().toString())
                .hostname(hostname)
                .requestTime(LocalDateTime.now())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .clientIp(getClientIp(request));
        
        // 获取请求参数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        ApiMonitor apiMonitor = signature.getMethod().getAnnotation(ApiMonitor.class);
        
        // 记录请求体
        if (apiMonitor.logRequest()) {
            try {
                Object[] args = joinPoint.getArgs();
                StringBuilder requestParams = new StringBuilder();
                
                for (Object arg : args) {
                    // 跳过请求和响应对象
                    if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                        continue;
                    }
                    
                    try {
                        // 转换为JSON字符串
                        String argJson = objectMapper.writeValueAsString(arg);
                        requestParams.append(argJson).append(", ");
                    } catch (Exception e) {
                        // 忽略序列化异常
                        requestParams.append(arg.toString()).append(", ");
                    }
                }
                
                logBuilder.requestParams(requestParams.toString());
            } catch (Exception e) {
                log.warn("记录请求参数失败", e);
            }
        }
        
        // 执行目标方法
        Object result = null;
        try {
            result = joinPoint.proceed();
            
            // 记录响应结果
            if (apiMonitor.logResponse() && result != null) {
                try {
                    String responseJson = objectMapper.writeValueAsString(result);
                    // 过长的响应可以截断
                    if (responseJson.length() > 1000) {
                        responseJson = responseJson.substring(0, 1000) + "...";
                    }
                    logBuilder.responseBody(responseJson);
                } catch (Exception e) {
                    log.warn("记录响应数据失败", e);
                    logBuilder.responseBody(result.toString());
                }
            }
            
            logBuilder.statusCode(200); // 假设成功是200
        } catch (Throwable ex) {
            // 记录异常信息
            logBuilder.statusCode(500); // 假设异常是500
            logBuilder.exceptionMsg(ex.getMessage());
            
            // 重新抛出异常
            throw ex;
        } finally {
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            logBuilder.executionTime(executionTime);
            
            // 异步保存日志
            ApiLog apiLog = logBuilder.build();
            try {
                logStore.store(apiLog);
            } catch (Exception e) {
                log.error("保存API日志失败", e);
            }
        }
        
        return result;
    }
    
    /**
     * 获取客户端真实IP
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