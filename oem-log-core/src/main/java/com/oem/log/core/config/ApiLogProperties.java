package com.oem.log.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * API日志配置属性
 */
@ConfigurationProperties(prefix = "oem.log")
@Data
public class ApiLogProperties {
    
    /**
     * 是否启用API日志记录
     */
    private boolean enabled = true;
    
    /**
     * 日志文件基础目录
     */
    private String baseDir = "./logs/api";
    
    /**
     * 日志保留天数
     */
    private int retentionDays = 7;
    
    /**
     * 单个日志文件大小(MB)
     */
    private int fileSizeMb = 64;
    
    /**
     * 批量刷盘阈值(条数)
     */
    private int flushThreshold = 100;
    
    /**
     * 批量刷盘间隔(毫秒)
     */
    private long flushIntervalMs = 100;
    
    /**
     * URL过滤模式（支持Ant风格路径，如/api/**）
     */
    private List<String> urlPatterns = Arrays.asList("/api/**");
    
    /**
     * 记录内容的最大长度
     */
    private int maxContentLength = 1000;
    
    /**
     * 是否记录请求体
     */
    private boolean logRequestBody = true;
    
    /**
     * 是否记录响应体
     */
    private boolean logResponseBody = true;
    
    /**
     * 是否记录请求头
     */
    private boolean logHeaders = false;
    
    /**
     * 监控配置
     */
    private Metrics metrics = new Metrics();
    
    @Data
    public static class Metrics {
        /**
         * 是否启用监控端点
         */
        private boolean enabled = true;
        
        /**
         * 指标刷新间隔(毫秒)
         */
        private long refreshInterval = 1000;
        
        /**
         * 监控端点访问路径
         */
        private String path = "/oem-log/metrics";
        
        /**
         * 性能警告阈值(毫秒)
         */
        private long performanceThreshold = 100;
    }
} 