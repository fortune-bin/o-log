package com.oem.log.viewer.model;

import com.oem.log.core.model.ApiLog;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 日志查询结果
 */
@Data
@Builder
public class LogQueryResult {
    
    /**
     * 总记录数
     */
    private int total;
    
    /**
     * 日志列表
     */
    private List<ApiLog> logs;
    
    /**
     * 提示信息
     */
    private String message;
} 