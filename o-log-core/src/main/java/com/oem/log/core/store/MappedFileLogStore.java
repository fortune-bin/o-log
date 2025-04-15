package com.oem.log.core.store;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.oem.log.core.metrics.LogMetrics;
import com.oem.log.core.model.ApiLog;
import com.oem.log.core.serializer.LogSerializer;
import jdk.internal.ref.Cleaner;
import lombok.extern.slf4j.Slf4j;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 基于内存映射文件的日志存储实现
 */
@Slf4j
public class MappedFileLogStore implements LogStore {
    
    private static final int DEFAULT_RING_BUFFER_SIZE = 1024 * 16; // 16K的环形缓冲区
    private static final long FILE_SIZE = 1024 * 1024 * 64; // 64MB的文件大小
    private static final int FLUSH_THRESHOLD = 100; // 积累100条记录批量刷盘
    private static final long FLUSH_INTERVAL_MS = 100; // 或者100毫秒定时刷盘
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 数据文件和索引文件目录
    private final String logDir;
    private final String indexDir;
    
    // Disruptor队列相关
    private Disruptor<LogEvent> disruptor;
    private RingBuffer<LogEvent> ringBuffer;
    
    // 当前操作的文件
    private volatile MappedFile currentDataFile;
    private volatile MappedFile currentIndexFile;
    
    // 文件自增序号
    private final AtomicLong fileSequence = new AtomicLong(0);
    
    // 缓存区，批量写入优化
    private final List<ApiLog> logCache = new ArrayList<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 序列化器
    private final LogSerializer serializer;
    
    // 本机主机名
    private final String hostname;
    
    // 定时刷盘调度器
    private ScheduledExecutorService scheduledExecutor;
    
    // 监控指标
    private final LogMetrics metrics = new LogMetrics();

    // 定时更新指标的任务
    private ScheduledExecutorService metricsExecutor;
    
    public MappedFileLogStore(String baseDir, LogSerializer serializer) {
        this.logDir = baseDir + File.separator + "data";
        this.indexDir = baseDir + File.separator + "index";
        this.serializer = serializer;
        
        try {
            // 获取主机名
            this.hostname = InetAddress.getLocalHost().getHostName();
            
            // 确保目录存在
            Path dataPath = Paths.get(logDir);
            Path indexPath = Paths.get(indexDir);
            Files.createDirectories(dataPath);
            Files.createDirectories(indexPath);
        } catch (Exception e) {
            throw new RuntimeException("初始化日志存储系统失败", e);
        }
    }
    
    /**
     * 获取监控指标
     */
    public LogMetrics getMetrics() {
        return metrics;
    }
    
    @Override
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            // 创建Disruptor，使用CPU亲和性提高性能
            ThreadFactory threadFactory = new AffinityThreadFactory("log-disruptor", AffinityStrategies.DIFFERENT_CORE);
            disruptor = new Disruptor<>(
                    LogEvent::new,
                    DEFAULT_RING_BUFFER_SIZE,
                    threadFactory,
                    ProducerType.MULTI,
                    new BlockingWaitStrategy()
            );
            
            // 设置处理器
            disruptor.handleEventsWith(this::processLogEvent);
            
            // 启动Disruptor
            ringBuffer = disruptor.start();
            
            // 启动定时刷盘
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutor.scheduleAtFixedRate(
                    this::flushIfNeeded,
                    FLUSH_INTERVAL_MS,
                    FLUSH_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
            );
            
            // 启动指标更新
            metricsExecutor = Executors.newSingleThreadScheduledExecutor();
            metricsExecutor.scheduleAtFixedRate(
                    this::updateMetrics,
                    1000,
                    1000,
                    TimeUnit.MILLISECONDS
            );
            
            log.info("日志存储系统已启动");
        }
    }

    @Override
    public void shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            // 停止定时任务
            if (scheduledExecutor != null) {
                scheduledExecutor.shutdown();
            }
            
            // 停止指标更新
            if (metricsExecutor != null) {
                metricsExecutor.shutdown();
            }
            
            // 强制刷盘
            synchronized (logCache) {
                if (!logCache.isEmpty()) {
                    flushToFile(logCache);
                    logCache.clear();
                }
            }
            
            // 关闭当前文件
            if (currentDataFile != null) {
                currentDataFile.close();
            }
            if (currentIndexFile != null) {
                currentIndexFile.close();
            }
            
            // 关闭Disruptor
            if (disruptor != null) {
                disruptor.shutdown();
            }
            
            log.info("日志存储系统已关闭");
        }
    }

    @Override
    public void store(ApiLog log) {
        if (!isRunning.get()) {
            throw new IllegalStateException("日志存储系统未启动");
        }
        
        metrics.recordProcessed();
        
        // 发布事件到Disruptor
        try {
            long sequence = ringBuffer.next();
            try {
                LogEvent event = ringBuffer.get(sequence);
                event.setApiLog(log);
            } finally {
                ringBuffer.publish(sequence);
            }
        } catch (Exception e) {
            metrics.recordError();
            throw e;
        }
    }

    @Override
    public void storeBatch(List<ApiLog> logs) {
        if (!isRunning.get()) {
            throw new IllegalStateException("日志存储系统未启动");
        }
        
        for (ApiLog log : logs) {
            store(log);
        }
    }

    @Override
    public List<ApiLog> query(Predicate<ApiLog> predicate, long startTime, long endTime, int limit) {
        // 简单实现：扫描索引和数据文件
        // 实际生产中应该实现更复杂的索引查询和过滤机制
        
        // 暂时返回空列表，后续实现查询
        return Collections.emptyList();
    }
    
    // Disruptor事件处理器
    private void processLogEvent(LogEvent event, long sequence, boolean endOfBatch) {
        ApiLog log = event.getApiLog();
        long startTime = System.currentTimeMillis();
        
        synchronized (logCache) {
            logCache.add(log);
            
            // 记录当前队列大小
            metrics.updateQueueSize(logCache.size());
            
            // 达到批量条数阈值时刷盘
            if (logCache.size() >= FLUSH_THRESHOLD || endOfBatch) {
                flushToFile(logCache);
                logCache.clear();
            } else if (logCache.size() >= FLUSH_THRESHOLD * 2) {
                // 当积压严重时记录缓存溢出事件
                metrics.recordCacheOverflow();
            }
        }
        
        // 记录处理延迟
        long latency = System.currentTimeMillis() - startTime;
        metrics.recordWriteLatency(latency);
    }
    
    // 定时检查是否需要刷盘
    private void flushIfNeeded() {
        synchronized (logCache) {
            if (!logCache.isEmpty()) {
                flushToFile(logCache);
                logCache.clear();
            }
        }
    }
    
    // 写入文件
    private void flushToFile(List<ApiLog> logs) {
        if (logs.isEmpty()) {
            return;
        }
        
        ensureFileAvailable();
        
        try {
            int totalBytes = 0;
            
            for (ApiLog log : logs) {
                ByteBuffer buffer = serializer.serialize(log);
                int size = buffer.remaining();
                totalBytes += size;
                
                // 获取当前数据在文件中的位置（用于索引）
                long position = currentDataFile.getWritePosition();
                
                // 写入数据文件
                currentDataFile.append(buffer);
                
                // 写入索引文件（简单索引：位置和大小）
                ByteBuffer indexBuffer = ByteBuffer.allocateDirect(8 + 4);
                indexBuffer.putLong(position);
                indexBuffer.putInt(size);
                indexBuffer.flip();
                currentIndexFile.append(indexBuffer);
            }
            
            // 更新指标
            metrics.recordBytes(totalBytes);
            
        } catch (Exception e) {
            metrics.recordError();
            log.error("写入日志文件失败", e);
        }
    }
    
    // 确保文件可用，如果文件已满则创建新文件
    private synchronized void ensureFileAvailable() {
        try {
            // 初始化或检查文件是否需要滚动
            if (currentDataFile == null || currentDataFile.isFull() || 
                    currentIndexFile == null || currentIndexFile.isFull()) {
                
                // 关闭当前文件
                if (currentDataFile != null) {
                    currentDataFile.close();
                }
                if (currentIndexFile != null) {
                    currentIndexFile.close();
                }
                
                // 创建新文件
                String timestamp = LocalDateTime.now().format(FILE_NAME_FORMATTER);
                long seq = fileSequence.incrementAndGet();
                
                String dataFileName = String.format("%s_%s_%d.data", hostname, timestamp, seq);
                String indexFileName = String.format("%s_%s_%d.index", hostname, timestamp, seq);
                
                currentDataFile = new MappedFile(logDir + File.separator + dataFileName, FILE_SIZE);
                currentIndexFile = new MappedFile(indexDir + File.separator + indexFileName, FILE_SIZE / 10); // 索引文件通常比数据文件小
                
                // 记录文件创建
                metrics.recordFileCreated();
            }
            
            // 更新文件大小指标
            metrics.updateFileSize(
                    currentDataFile != null ? currentDataFile.getWritePosition() : 0,
                    currentIndexFile != null ? currentIndexFile.getWritePosition() : 0
            );
            
        } catch (Exception e) {
            metrics.recordError();
            log.error("创建日志文件失败", e);
            throw new RuntimeException("创建日志文件失败", e);
        }
    }
    
    // 更新指标信息
    private void updateMetrics() {
        try {
            if (ringBuffer != null) {
                // 更新队列使用情况
                metrics.updateQueueSize(ringBuffer.getBufferSize() - ringBuffer.remainingCapacity());
            }
            
            // 更新文件大小
            if (currentDataFile != null && currentIndexFile != null) {
                metrics.updateFileSize(
                        currentDataFile.getWritePosition(),
                        currentIndexFile.getWritePosition()
                );
            }
        } catch (Exception e) {
            log.warn("更新指标失败", e);
        }
    }
    
    // Disruptor事件
    public static class LogEvent {
        private ApiLog apiLog;
        
        public ApiLog getApiLog() {
            return apiLog;
        }
        
        public void setApiLog(ApiLog apiLog) {
            this.apiLog = apiLog;
        }
    }
    
    // 映射文件封装类
    private static class MappedFile {
        private final FileChannel channel;
        private final MappedByteBuffer mappedBuffer;
        private final long fileSize;
        private final AtomicLong writePosition = new AtomicLong(0);
        
        public MappedFile(String fileName, long fileSize) throws IOException {
            this.fileSize = fileSize;
            
            File file = new File(fileName);
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
            
            // 创建内存映射
            mappedBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
        }
        
        public void append(ByteBuffer buffer) {
            int size = buffer.remaining();
            
            if (writePosition.get() + size > fileSize) {
                throw new IllegalStateException("文件已满");
            }
            
            // 拷贝数据到映射缓冲区
            mappedBuffer.position((int) writePosition.get());
            mappedBuffer.put(buffer);
            
            // 更新写位置
            writePosition.addAndGet(size);
        }
        
        public long getWritePosition() {
            return writePosition.get();
        }
        
        public boolean isFull() {
            // 预留1KB，避免精确填满
            return writePosition.get() > fileSize - 1024;
        }
        
        public void close() {
            // 强制刷盘
            if (mappedBuffer != null) {
                mappedBuffer.force();
            }
            
            // 关闭通道
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                log.error("关闭文件通道失败", e);
            }
            
            // 释放DirectBuffer
            try {
                // 使用反射手动触发cleaner清理DirectBuffer
                Cleaner cleaner = ((DirectBuffer) mappedBuffer).cleaner();
                if (cleaner != null) {
                    cleaner.clean();
                }
            } catch (Exception e) {
                log.error("清理DirectBuffer失败", e);
            }
        }
    }
} 