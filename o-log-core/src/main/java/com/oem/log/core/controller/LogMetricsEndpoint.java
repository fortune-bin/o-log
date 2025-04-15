package com.oem.log.core.controller;

import com.oem.log.core.metrics.LogMetrics;
import com.oem.log.core.store.LogStore;
import com.oem.log.core.store.MappedFileLogStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志指标监控端点
 */
@RestController
@RequestMapping("/oem-log")
@ConditionalOnProperty(prefix = "oem.log.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogMetricsEndpoint {
    
    @Autowired
    private LogStore logStore;
    
    /**
     * 获取日志系统指标
     */
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        if (logStore instanceof MappedFileLogStore) {
            LogMetrics metrics = ((MappedFileLogStore) logStore).getMetrics();
            return createMetricsMap(metrics);
        }
        
        return new HashMap<>();
    }
    
    /**
     * 重置最大写入延迟指标
     */
    @GetMapping("/metrics/reset-latency")
    public Map<String, Object> resetMaxLatency() {
        if (logStore instanceof MappedFileLogStore) {
            LogMetrics metrics = ((MappedFileLogStore) logStore).getMetrics();
            metrics.resetMaxWriteLatency();
            return createMetricsMap(metrics);
        }
        
        return new HashMap<>();
    }
    
    private Map<String, Object> createMetricsMap(LogMetrics metrics) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("totalProcessed", metrics.getTotalProcessed());
        result.put("recentProcessed", metrics.getRecentProcessed());
        result.put("totalErrors", metrics.getTotalErrors());
        result.put("currentQueueSize", metrics.getCurrentQueueSize());
        result.put("totalBytes", metrics.getTotalBytes());
        result.put("maxWriteLatency", metrics.getMaxWriteLatency() + " ms");
        result.put("currentFileSize", formatSize(metrics.getCurrentFileSize()));
        result.put("currentIndexSize", formatSize(metrics.getCurrentIndexSize()));
        result.put("totalFiles", metrics.getTotalFiles());
        result.put("cacheOverflows", metrics.getCacheOverflows());
        
        Map<String, Object> status = new HashMap<>();
        status.put("health", metrics.getTotalErrors() > 0 ? "warning" : "good");
        status.put("performance", metrics.getMaxWriteLatency() > 100 ? "warning" : "good");
        result.put("status", status);
        
        return result;
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
    
    /**
     * Actuator 端点配置
     */
    @Configuration
    @ConditionalOnClass(Endpoint.class)
    static class LogMetricsActuatorConfiguration {
        
        @Bean
        @Endpoint(id = "oemlog")
        public LogMetricsActuator logMetricsActuator(LogStore logStore) {
            return new LogMetricsActuator(logStore);
        }
    }
    
    /**
     * Actuator 端点实现
     */
    static class LogMetricsActuator {
        
        private final LogStore logStore;
        
        public LogMetricsActuator(LogStore logStore) {
            this.logStore = logStore;
        }
        
        @ReadOperation
        public Map<String, Object> metrics() {
            if (logStore instanceof MappedFileLogStore) {
                LogMetrics metrics = ((MappedFileLogStore) logStore).getMetrics();
                
                Map<String, Object> result = new HashMap<>();
                result.put("totalProcessed", metrics.getTotalProcessed());
                result.put("recentProcessed", metrics.getRecentProcessed());
                result.put("totalErrors", metrics.getTotalErrors());
                result.put("currentQueueSize", metrics.getCurrentQueueSize());
                result.put("totalBytes", metrics.getTotalBytes());
                result.put("maxWriteLatency", metrics.getMaxWriteLatency());
                result.put("totalFiles", metrics.getTotalFiles());
                
                return result;
            }
            
            return new HashMap<>();
        }
    }
} 