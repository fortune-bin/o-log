package com.oem.log.viewer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oem.log.core.model.ApiLog;
import com.oem.log.core.serializer.JsonLogSerializer;
import com.oem.log.viewer.model.LogQueryRequest;
import com.oem.log.viewer.model.LogQueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 日志查询服务
 */
@Service
@Slf4j
public class LogQueryService {
    
    @Value("${oem.log.query.search-dir}")
    private String searchDir;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final ExecutorService queryExecutor = Executors.newFixedThreadPool(4);
    private final JsonLogSerializer serializer = new JsonLogSerializer();
    
    /**
     * 查询日志
     * @param request 查询请求
     * @return 查询结果
     */
    public LogQueryResult queryLogs(LogQueryRequest request) {
        try {
            // 查找时间范围内的日志文件
            File dir = new File(searchDir);
            if (!dir.exists() || !dir.isDirectory()) {
                return LogQueryResult.builder()
                        .total(0)
                        .logs(new ArrayList<>())
                        .message("日志目录不存在")
                        .build();
            }
            
            // 获取符合条件的日志文件
            File[] dataFiles = dir.listFiles(file -> {
                return file.getName().endsWith(".data") && isFileInTimeRange(file.getName(), request);
            });
            
            if (dataFiles == null || dataFiles.length == 0) {
                return LogQueryResult.builder()
                        .total(0)
                        .logs(new ArrayList<>())
                        .message("未找到符合条件的日志文件")
                        .build();
            }
            
            // 并行处理每个文件
            List<CompletableFuture<List<ApiLog>>> futures = new ArrayList<>();
            for (File dataFile : dataFiles) {
                CompletableFuture<List<ApiLog>> future = CompletableFuture.supplyAsync(
                        () -> searchLogsInFile(dataFile, request),
                        queryExecutor
                );
                futures.add(future);
            }
            
            // 等待所有查询完成并合并结果
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );
            
            CompletableFuture<List<ApiLog>> resultFuture = allFutures.thenApply(v -> {
                return futures.stream()
                        .flatMap(f -> f.join().stream())
                        .collect(Collectors.toList());
            });
            
            List<ApiLog> allLogs = resultFuture.get();
            
            // 按时间倒序排序
            allLogs.sort((o1, o2) -> o2.getRequestTime().compareTo(o1.getRequestTime()));
            
            // 应用分页
            int startIndex = (request.getPage() - 1) * request.getPageSize();
            int endIndex = Math.min(startIndex + request.getPageSize(), allLogs.size());
            
            if (startIndex >= allLogs.size()) {
                return LogQueryResult.builder()
                        .total(allLogs.size())
                        .logs(new ArrayList<>())
                        .build();
            }
            
            List<ApiLog> pagedLogs = allLogs.subList(startIndex, endIndex);
            
            return LogQueryResult.builder()
                    .total(allLogs.size())
                    .logs(pagedLogs)
                    .build();
            
        } catch (Exception e) {
            log.error("查询日志失败", e);
            return LogQueryResult.builder()
                    .total(0)
                    .logs(new ArrayList<>())
                    .message("查询日志失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 从单个文件中查询日志
     */
    private List<ApiLog> searchLogsInFile(File dataFile, LogQueryRequest request) {
        List<ApiLog> result = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(dataFile);
             FileChannel channel = fis.getChannel()) {
            
            // 使用内存映射读取文件提高性能
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            
            while (buffer.hasRemaining()) {
                // 读取记录长度
                int length = buffer.getInt();
                
                // 读取日志数据
                byte[] data = new byte[length];
                buffer.get(data);
                
                // 反序列化
                ByteBuffer logBuffer = ByteBuffer.allocateDirect(4 + length);
                logBuffer.putInt(length);
                logBuffer.put(data);
                logBuffer.flip();
                
                ApiLog log = serializer.deserialize(logBuffer);
                
                // 应用过滤条件
                if (matchesFilter(log, request)) {
                    result.add(log);
                }
            }
            
        } catch (Exception e) {
            log.error("读取日志文件失败: " + dataFile.getName(), e);
        }
        
        return result;
    }
    
    /**
     * 判断日志是否匹配过滤条件
     */
    private boolean matchesFilter(ApiLog log, LogQueryRequest request) {
        // 时间范围过滤
        LocalDateTime startTime = LocalDateTime.ofInstant(request.getStartTime().toInstant(), ZoneId.systemDefault());
        LocalDateTime endTime = LocalDateTime.ofInstant(request.getEndTime().toInstant(), ZoneId.systemDefault());
        
        if (log.getRequestTime().isBefore(startTime) || log.getRequestTime().isAfter(endTime)) {
            return false;
        }
        
        // 路径过滤
        if (request.getPath() != null && !request.getPath().isEmpty()) {
            if (log.getPath() == null || !log.getPath().contains(request.getPath())) {
                return false;
            }
        }
        
        // 状态码过滤
        if (request.getStatusCode() > 0) {
            if (log.getStatusCode() != request.getStatusCode()) {
                return false;
            }
        }
        
        // 执行时间过滤
        if (request.getMinDuration() > 0 && log.getExecutionTime() < request.getMinDuration()) {
            return false;
        }
        if (request.getMaxDuration() > 0 && log.getExecutionTime() > request.getMaxDuration()) {
            return false;
        }
        
        // 异常信息过滤
        if (request.getErrorKeyword() != null && !request.getErrorKeyword().isEmpty()) {
            if (log.getExceptionMsg() == null || !log.getExceptionMsg().contains(request.getErrorKeyword())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 判断文件是否在时间范围内（基于文件名）
     */
    private boolean isFileInTimeRange(String fileName, LogQueryRequest request) {
        // 简单实现：假设任何文件都可能包含时间范围内的日志
        // 实际生产中可以从文件名解析时间戳做更精确的筛选
        return true;
    }
} 