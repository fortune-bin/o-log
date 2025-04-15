# OEM-Log 过滤器使用指南

## 基本使用

OEM-Log现在基于过滤器实现，替代了原有的AOP方式。这带来了以下优势：

1. 更高性能：避免了AOP代理带来的性能开销
2. 更灵活定制：通过SPI机制支持自定义处理逻辑
3. 更简单集成：自动配置，无需额外代码

## 配置参数

在`application.yml`中可以进行以下配置：

```yaml
oem:
  log:
    enabled: true                    # 是否启用日志记录
    base-dir: ./logs/api             # 日志存储目录
    url-patterns:                    # URL匹配模式(Ant风格)
      - /api/**                      # 例：只记录/api/开头的请求
    max-content-length: 1000         # 内容截断长度
    log-request-body: true           # 是否记录请求体
    log-response-body: true          # 是否记录响应体
    log-headers: false               # 是否记录请求头
```

## 自定义处理器

通过SPI机制，你可以创建自定义的日志处理器，实现特定业务需求：

1. 实现`ApiLogProcessor`接口：

```java
public class MyCustomProcessor implements ApiLogProcessor {
    
    @Override
    public void processRequest(ContentCachingRequestWrapper request, ApiLog.ApiLogBuilder logBuilder) {
        // 自定义请求处理逻辑
    }
    
    @Override
    public void processResponse(ContentCachingResponseWrapper response, ApiLog.ApiLogBuilder logBuilder) {
        // 自定义响应处理逻辑
    }
    
    @Override
    public void processException(Exception exception, ApiLog.ApiLogBuilder logBuilder) {
        // 自定义异常处理逻辑
    }
    
    @Override
    public boolean shouldProcess(HttpServletRequest request) {
        // 决定是否处理此请求
        return request.getRequestURI().contains("/important/");
    }
}
```

2. 注册SPI服务：

创建文件：`META-INF/services/com.oem.log.core.filter.ApiLogProcessor`，内容为你的实现类全名：

```
com.yourcompany.MyCustomProcessor
```

3. 程序化注册：

你也可以通过代码注册处理器：

```java
@Configuration
public class MyLogConfig {
    
    @Autowired
    private ApiLogFilter apiLogFilter;
    
    @PostConstruct
    public void init() {
        // 注册自定义处理器
        apiLogFilter.addProcessor(new MyCustomProcessor());
    }
}
```

## 处理器示例

参考`ApiPathLogProcessor`作为示例：

```java
public class ApiPathLogProcessor implements ApiLogProcessor {
    
    private final String[] pathPrefixes;
    
    public ApiPathLogProcessor(String... pathPrefixes) {
        this.pathPrefixes = pathPrefixes;
    }
    
    @Override
    public boolean shouldProcess(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : pathPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    // 其他方法实现...
}
```

## 注意事项

1. 处理器的执行顺序由SPI加载顺序决定
2. 多个处理器可能会处理同一个请求
3. 异常会正常传播给客户端
4. 内容可能会被截断以防止日志过大 