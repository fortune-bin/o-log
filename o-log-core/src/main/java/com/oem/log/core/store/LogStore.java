package com.oem.log.core.store;

import com.oem.log.core.model.ApiLog;

import java.util.List;
import java.util.function.Predicate;

/**
 * 日志存储接口
 */
public interface LogStore {
    
    /**
     * 存储单条日志
     * @param log 日志对象
     */
    void store(ApiLog log);
    
    /**
     * 批量存储日志
     * @param logs 日志列表
     */
    void storeBatch(List<ApiLog> logs);
    
    /**
     * 根据条件查询日志
     * @param predicate 过滤条件
     * @param startTime 开始时间戳（毫秒）
     * @param endTime 结束时间戳（毫秒）
     * @param limit 最大返回数量
     * @return 符合条件的日志列表
     */
    List<ApiLog> query(Predicate<ApiLog> predicate, long startTime, long endTime, int limit);
    
    /**
     * 启动存储服务
     */
    void start();
    
    /**
     * 关闭存储服务
     */
    void shutdown();
} 