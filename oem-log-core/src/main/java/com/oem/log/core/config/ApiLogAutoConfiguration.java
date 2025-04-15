package com.oem.log.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oem.log.core.serializer.JsonLogSerializer;
import com.oem.log.core.serializer.LogSerializer;
import com.oem.log.core.store.LogStore;
import com.oem.log.core.store.MappedFileLogStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * API日志自动配置类
 */
@Configuration
@EnableConfigurationProperties(ApiLogProperties.class)
@ConditionalOnProperty(prefix = "oem.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(ApiLogFilterConfig.class)
public class ApiLogAutoConfiguration {
    
    @Value("${oem.log.base-dir:./logs/api}")
    private String baseDir;
    
    private LogStore logStore;
    
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LogSerializer logSerializer() {
        return new JsonLogSerializer();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public LogStore logStore(LogSerializer logSerializer) {
        logStore = new MappedFileLogStore(baseDir, logSerializer);
        return logStore;
    }
    
    @PostConstruct
    public void init() {
        if (logStore != null) {
            logStore.start();
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (logStore != null) {
            logStore.shutdown();
        }
    }
} 