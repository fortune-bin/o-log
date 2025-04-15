package com.oem.log.core.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 日志监控指标
 */
public class LogMetrics {
    
    // 总请求处理数
    private final LongAdder totalProcessed = new LongAdder();
    
    // 最近一分钟请求数
    private final LongAdder recentProcessed = new LongAdder();
    
    // 最近重置时间
    private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
    
    // 总错误数
    private final LongAdder totalErrors = new LongAdder();
    
    // 当前写入队列大小
    private final AtomicLong currentQueueSize = new AtomicLong(0);
    
    // 累计字节数
    private final LongAdder totalBytes = new LongAdder();
    
    // 最大写入延迟(毫秒)
    private final AtomicLong maxWriteLatency = new AtomicLong(0);
    
    // 当前文件大小
    private final AtomicLong currentFileSize = new AtomicLong(0);
    
    // 当前索引文件大小
    private final AtomicLong currentIndexSize = new AtomicLong(0);
    
    // 总文件数
    private final AtomicLong totalFiles = new AtomicLong(0);
    
    // 缓存溢出次数
    private final LongAdder cacheOverflows = new LongAdder();
    
    // 记录处理请求
    public void recordProcessed() {
        totalProcessed.increment();
        recentProcessed.increment();
        
        // 每分钟重置最近处理计数
        long now = System.currentTimeMillis();
        long last = lastResetTime.get();
        if (now - last > 60000 && lastResetTime.compareAndSet(last, now)) {
            recentProcessed.reset();
        }
    }
    
    // 记录错误
    public void recordError() {
        totalErrors.increment();
    }
    
    // 更新队列大小
    public void updateQueueSize(long size) {
        currentQueueSize.set(size);
    }
    
    // 记录写入字节数
    public void recordBytes(long bytes) {
        totalBytes.add(bytes);
    }
    
    // 记录写入延迟
    public void recordWriteLatency(long latencyMs) {
        while (true) {
            long current = maxWriteLatency.get();
            if (latencyMs <= current || maxWriteLatency.compareAndSet(current, latencyMs)) {
                break;
            }
        }
    }
    
    // 更新文件大小
    public void updateFileSize(long dataSize, long indexSize) {
        currentFileSize.set(dataSize);
        currentIndexSize.set(indexSize);
    }
    
    // 记录文件创建
    public void recordFileCreated() {
        totalFiles.incrementAndGet();
    }
    
    // 记录缓存溢出
    public void recordCacheOverflow() {
        cacheOverflows.increment();
    }
    
    // 获取总处理数
    public long getTotalProcessed() {
        return totalProcessed.sum();
    }
    
    // 获取最近处理数
    public long getRecentProcessed() {
        return recentProcessed.sum();
    }
    
    // 获取总错误数
    public long getTotalErrors() {
        return totalErrors.sum();
    }
    
    // 获取当前队列大小
    public long getCurrentQueueSize() {
        return currentQueueSize.get();
    }
    
    // 获取总字节数
    public long getTotalBytes() {
        return totalBytes.sum();
    }
    
    // 获取最大写入延迟
    public long getMaxWriteLatency() {
        return maxWriteLatency.get();
    }
    
    // 获取当前文件大小
    public long getCurrentFileSize() {
        return currentFileSize.get();
    }
    
    // 获取当前索引文件大小
    public long getCurrentIndexSize() {
        return currentIndexSize.get();
    }
    
    // 获取总文件数
    public long getTotalFiles() {
        return totalFiles.get();
    }
    
    // 获取缓存溢出次数
    public long getCacheOverflows() {
        return cacheOverflows.sum();
    }
    
    // 重置最大写入延迟
    public void resetMaxWriteLatency() {
        maxWriteLatency.set(0);
    }
} 