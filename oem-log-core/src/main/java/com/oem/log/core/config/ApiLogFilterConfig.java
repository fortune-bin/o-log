package com.oem.log.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oem.log.core.filter.ApiLogFilter;
import com.oem.log.core.filter.DefaultApiLogProcessor;
import com.oem.log.core.store.LogStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * API日志过滤器配置
 */
@Configuration
@ConditionalOnProperty(prefix = "oem.log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiLogFilterConfig {

    @Value("${oem.log.url-patterns:/api/**}")
    private String[] urlPatterns;
    
    @Value("${oem.log.max-content-length:1000}")
    private int maxContentLength;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public FilterRegistrationBean<ApiLogFilter> apiLogFilterRegistration(LogStore logStore) {
        List<String> patterns = urlPatterns != null ? 
                Arrays.asList(urlPatterns) : 
                Collections.singletonList("/*");
        
        ApiLogFilter filter = new ApiLogFilter(logStore, patterns);
        
        // 添加默认处理器
        filter.addProcessor(new DefaultApiLogProcessor(objectMapper, maxContentLength));
        
        FilterRegistrationBean<ApiLogFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setUrlPatterns(patterns);
        registration.setName("apiLogFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100); // 确保在基本过滤器之后执行
        
        return registration;
    }
} 