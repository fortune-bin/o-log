# OEM-Log 监控指标功能开发笔记

## 设计思路

OEM-Log系统中的监控指标功能旨在提供对日志写入性能和健康状况的实时监控，帮助开发和运维人员及时发现问题。设计时考虑了以下几点：

1. **低侵入性**：监控逻辑不影响核心写入流程的性能
2. **自动装配**：依赖引入后自动启用，无需额外配置
3. **丰富指标**：覆盖请求量、错误率、延迟、文件大小等多维度
4. **可视化界面**：提供直观的图形界面，易于观察和分析
5. **可配置性**：支持灵活的配置选项，满足不同场景需求

## 核心组件

### 1. LogMetrics 类

这是指标收集的核心类，使用高性能的原子计数器收集各类指标：

```java
public class LogMetrics {
    // 总请求处理数
    private final LongAdder totalProcessed = new LongAdder();
    
    // 最近一分钟请求数
    private final LongAdder recentProcessed = new LongAdder();
    
    // 总错误数
    private final LongAdder totalErrors = new LongAdder();
    
    // 当前写入队列大小
    private final AtomicLong currentQueueSize = new AtomicLong(0);
    
    // 累计字节数
    private final LongAdder totalBytes = new LongAdder();
    
    // 最大写入延迟(毫秒) 
    private final AtomicLong maxWriteLatency = new AtomicLong(0);
    
    // 文件相关指标
    private final AtomicLong currentFileSize = new AtomicLong(0);
    private final AtomicLong currentIndexSize = new AtomicLong(0);
    private final AtomicLong totalFiles = new AtomicLong(0);
    
    // 缓存溢出次数
    private final LongAdder cacheOverflows = new LongAdder();
    
    // 更新和获取方法...
}
```

特点：
- 使用`LongAdder`代替`AtomicLong`计数，在高并发场景下性能更好
- 针对不同类型的指标使用不同的计数器类型
- 提供原子性的更新操作，确保指标准确性

### 2. 监控点植入

在`MappedFileLogStore`类中的关键点添加指标收集逻辑：

```java
// 处理日志事件时
private void processLogEvent(LogEvent event, long sequence, boolean endOfBatch) {
    ApiLog log = event.getApiLog();
    long startTime = System.currentTimeMillis();
    
    synchronized (logCache) {
        logCache.add(log);
        
        // 记录当前队列大小
        metrics.updateQueueSize(logCache.size());
        
        // 刷盘逻辑...
    }
    
    // 记录处理延迟
    long latency = System.currentTimeMillis() - startTime;
    metrics.recordWriteLatency(latency);
}

// 存储日志时
@Override
public void store(ApiLog log) {
    if (!isRunning.get()) {
        throw new IllegalStateException("日志存储系统未启动");
    }
    
    metrics.recordProcessed();
    
    // 发布事件到Disruptor...
}

// 写入文件时
private void flushToFile(List<ApiLog> logs) {
    // ...
    
    int totalBytes = 0;
    for (ApiLog log : logs) {
        ByteBuffer buffer = serializer.serialize(log);
        int size = buffer.remaining();
        totalBytes += size;
        
        // 写入逻辑...
    }
    
    // 更新指标
    metrics.recordBytes(totalBytes);
}
```

### 3. REST端点

创建`LogMetricsEndpoint`暴露指标数据：

```java
@RestController
@RequestMapping("/oem-log")
@ConditionalOnProperty(prefix = "oem.log.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogMetricsEndpoint {
    
    @Autowired
    private LogStore logStore;
    
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        if (logStore instanceof MappedFileLogStore) {
            LogMetrics metrics = ((MappedFileLogStore) logStore).getMetrics();
            return createMetricsMap(metrics);
        }
        return new HashMap<>();
    }
    
    @GetMapping("/metrics/reset-latency")
    public Map<String, Object> resetMaxLatency() {
        // 重置最大延迟指标...
    }
    
    // 创建指标Map
    private Map<String, Object> createMetricsMap(LogMetrics metrics) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("totalProcessed", metrics.getTotalProcessed());
        result.put("recentProcessed", metrics.getRecentProcessed());
        result.put("totalErrors", metrics.getTotalErrors());
        // 更多指标...
        
        // 添加状态判断
        Map<String, Object> status = new HashMap<>();
        status.put("health", metrics.getTotalErrors() > 0 ? "warning" : "good");
        status.put("performance", metrics.getMaxWriteLatency() > 100 ? "warning" : "good");
        result.put("status", status);
        
        return result;
    }
}
```

### 4. 配置选项

在`ApiLogProperties`中添加监控相关配置：

```java
@ConfigurationProperties(prefix = "oem.log")
@Data
public class ApiLogProperties {
    // 其他配置...
    
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
```

### 5. 可视化界面

创建了`log-metrics.html`页面，使用JavaScript和Bootstrap实现直观的监控面板：

- 实时展示各项指标的当前值
- 根据指标状态动态变换颜色
- 自动刷新数据，定期更新界面
- 支持手动刷新和延迟重置

## 收集的关键指标

| 指标名称 | 描述 | 意义 |
|---------|------|------|
| totalProcessed | 总处理请求数 | 反映系统总体处理量 |
| recentProcessed | 最近一分钟处理量 | 反映系统当前负载 |
| totalErrors | 总错误数 | 系统稳定性指标 |
| currentQueueSize | 当前队列大小 | 反映系统积压情况 |
| maxWriteLatency | 最大写入延迟 | 性能瓶颈指标 |
| totalBytes | 累计数据量 | 反映总体数据规模 |
| currentFileSize | 当前文件大小 | 监控单文件增长 |
| totalFiles | 总文件数 | 监控文件增长趋势 |
| cacheOverflows | 缓存溢出次数 | 内存压力指标 |

## 使用方法

### 1. 引入依赖

项目只需引入oem-log-core依赖，即可自动获得监控功能：

```xml
<dependency>
    <groupId>com.oem</groupId>
    <artifactId>oem-log-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置选项

可通过Spring Boot配置文件自定义监控行为：

```yaml
oem:
  log:
    metrics:
      enabled: true                 # 是否启用监控
      refresh-interval: 1000        # 指标刷新间隔(毫秒)
      path: /oem-log/metrics        # 监控端点路径
      performance-threshold: 100    # 性能警告阈值(毫秒)
```

### 3. 访问监控界面

- JSON格式数据：`/oem-log/metrics`
- 可视化界面：`/log-metrics.html`

### 4. Spring Boot Actuator集成

如果项目使用了Spring Boot Actuator，可以通过标准端点访问指标：

```
/actuator/oemlog
```

需要添加Actuator依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

并开放端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: oemlog
```

## 监控最佳实践

1. **定期检查指标**：建立常规检查流程，关注异常趋势
2. **设置告警阈值**：基于业务场景设置合理的性能和错误阈值
3. **关注峰值指标**：maxWriteLatency等峰值指标对发现性能问题至关重要
4. **结合日志分析**：将监控指标与日志内容结合分析
5. **监控磁盘空间**：关注totalBytes和totalFiles，及时清理过期日志

## 性能考量

监控功能设计时充分考虑了性能影响：

1. 使用高性能的`LongAdder`代替`AtomicLong`，减少高并发场景下的锁争用
2. 关键路径上的指标收集使用非阻塞操作
3. 独立的线程定期更新指标，不影响主要业务流程
4. 监控UI采用客户端渲染，减轻服务端压力

## 未来扩展

监控系统后续可考虑的扩展方向：

1. 接入Prometheus等监控系统，提供标准metrics端点
2. 增加历史指标存储，支持趋势分析
3. 添加自定义告警机制，在指标异常时主动通知
4. 扩展更多维度的指标，如GC情况、线程池状态等
5. 图表化展示，增强可视化效果 