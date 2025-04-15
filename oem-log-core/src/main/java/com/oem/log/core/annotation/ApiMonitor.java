package com.oem.log.core.annotation;

import java.lang.annotation.*;

/**
 * API监控注解，用于标记需要监控的接口方法
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiMonitor {
    
    /**
     * 是否记录请求参数
     */
    boolean logRequest() default true;
    
    /**
     * 是否记录响应结果
     */
    boolean logResponse() default true;
} 