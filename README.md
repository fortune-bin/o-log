# OEM-Log：高性能API日志监控系统

OEM-Log是一个基于Spring Boot的高性能API日志监控系统，用于记录和分析重要接口的请求、响应和错误信息。该系统不依赖任何数据库，使用内存映射文件直接将日志写入文件系统，并提供高效的查询功能。

## 核心特性

- **高性能文件写入**：使用内存映射文件和Disruptor队列实现高吞吐量日志写入
- **自动收集API信息**：通过过滤器自动拦截并记录接口请求、响应和错误信息
- **零代码侵入**：使用URL模式匹配监控接口，无需修改业务代码
- **自动装载**：依赖即生效，无需额外配置
- **SPI扩展机制**：支持通过SPI接口自定义处理逻辑
- **多节点支持**：支持多节点环境下的日志管理，文件名自动包含主机名
- **高效查询**：提供基于时间、路径、状态码等多维度的查询功能
- **友好界面**：直观的Web界面进行日志分析和排查

## 系统架构

系统分为两个主要模块：

1. **oem-log-core**：核心日志收集模块，作为依赖提供给业务系统
   - 提供过滤器机制收集API调用数据
   - 实现基于内存映射文件的高性能存储
   - 使用Disruptor队列实现异步批量写入
   
2. **oem-log-viewer**：日志查询与分析模块，独立部署
   - 提供Web界面查询日志
   - 支持多维度过滤和分析
   - 展示详细的接口调用信息

## 技术实现

- **过滤器拦截**：基于Servlet过滤器实现高性能请求拦截
- **SPI扩展**：支持自定义处理器
- **顺序写盘**：确保文件写入的高性能
- **异步批量刷盘**：积累100条或100ms批量写入
- **文件分片**：自动按大小分片文件
- **日志与索引分离**：数据文件与索引文件分离存储
- **CPU亲和性**：Disruptor线程绑定到特定CPU核心
- **DirectBuffer优化**：使用堆外内存避免GC压力
- **页面缓存优化**：利用操作系统PageCache提升性能

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.oem</groupId>
    <artifactId>oem-log-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置（可选）

```yaml
# application.yml
oem:
  log:
    enabled: true                    # 是否启用日志记录
    base-dir: /logs/api              # 日志存储目录
    url-patterns:                    # URL匹配模式(Ant风格)
      - /api/**                      # 例：只记录/api/开头的请求
    max-content-length: 1000         # 内容截断长度
    retention-days: 7                # 日志保留天数
```

### 3. 自定义处理器（可选）

通过SPI机制创建自定义处理器：

```java
public class CustomProcessor implements ApiLogProcessor {
    @Override
    public void processRequest(ContentCachingRequestWrapper request, ApiLog.ApiLogBuilder logBuilder) {
        // 处理请求
    }
    
    @Override
    public void processResponse(ContentCachingResponseWrapper response, ApiLog.ApiLogBuilder logBuilder) {
        // 处理响应
    }
    
    @Override
    public void processException(Exception exception, ApiLog.ApiLogBuilder logBuilder) {
        // 处理异常
    }
    
    @Override
    public boolean shouldProcess(HttpServletRequest request) {
        // 决定是否处理此请求
        return true;
    }
}
```

然后在META-INF/services目录下注册SPI服务。

### 4. 部署日志查看器

单独部署oem-log-viewer模块，访问其Web界面查看和分析日志。

```bash
java -jar oem-log-viewer.jar --oem.log.query.search-dir=/path/to/logs
```

## 配置说明

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| oem.log.enabled | 是否启用日志记录 | true |
| oem.log.base-dir | 日志文件基础目录 | ./logs/api |
| oem.log.url-patterns | URL匹配模式 | ["/api/**"] |
| oem.log.retention-days | 日志保留天数 | 7 |
| oem.log.max-content-length | 内容截断长度 | 1000 |
| oem.log.file-size-mb | 单个日志文件大小(MB) | 64 |
| oem.log.flush-threshold | 批量刷盘阈值(条数) | 100 |
| oem.log.flush-interval-ms | 批量刷盘间隔(毫秒) | 100 |
| oem.log.log-request-body | 是否记录请求体 | true |
| oem.log.log-response-body | 是否记录响应体 | true |
| oem.log.log-headers | 是否记录请求头 | false |

## 查询功能

日志查询支持以下条件：

- 时间范围选择器（支持快捷选项）
- 接口路径模糊查询
- 状态码筛选
- 耗时区间过滤
- 异常信息关键词搜索

## 性能优化

系统在设计上充分考虑了性能因素：

1. **过滤器机制**：比AOP更高效的请求拦截
2. **批量合并写入**：累积日志后批量写入，减少IO操作
3. **异步写入**：使用Disruptor高性能队列，不阻塞业务线程
4. **内存映射**：使用MappedByteBuffer实现高效读写
5. **堆外内存**：使用DirectByteBuffer减少GC压力
6. **文件分片**：文件按大小自动分片，提高查询效率

## 开发与扩展

系统设计为可扩展的架构，支持自定义：

1. 日志处理器（通过SPI机制）
2. 序列化器
3. 存储实现
4. 索引策略
5. 查询引擎

## 注意事项

- 请确保日志目录有足够的磁盘空间
- 生产环境建议配置日志保留策略
- 多节点环境下，确保日志查看器能访问所有节点的日志目录 