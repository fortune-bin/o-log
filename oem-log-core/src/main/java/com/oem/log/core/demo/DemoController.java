package com.oem.log.core.demo;

import com.oem.log.core.annotation.ApiMonitor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 演示用的控制器
 * 注意：实际使用时，依赖方只需在自己的Controller方法上添加@ApiMonitor注解即可
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    
    private final Random random = new Random();
    
    /**
     * 正常接口
     */
    @ApiMonitor
    @GetMapping("/success")
    public Map<String, Object> success(@RequestParam(defaultValue = "test") String name) {
        // 模拟业务处理
        try {
            Thread.sleep(random.nextInt(100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", "Hello, " + name);
        return result;
    }
    
    /**
     * 慢接口
     */
    @ApiMonitor
    @GetMapping("/slow")
    public Map<String, Object> slow() {
        // 模拟耗时操作
        try {
            Thread.sleep(1000 + random.nextInt(2000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "操作成功，但较慢");
        result.put("data", "Slow response");
        return result;
    }
    
    /**
     * 异常接口
     */
    @ApiMonitor
    @GetMapping("/error")
    public Map<String, Object> error() {
        // 模拟异常
        if (random.nextBoolean()) {
            throw new RuntimeException("模拟业务异常");
        } else {
            throw new IllegalArgumentException("模拟参数异常");
        }
    }
    
    /**
     * POST接口示例
     */
    @ApiMonitor
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody UserRequest request) {
        // 模拟业务处理
        try {
            Thread.sleep(random.nextInt(500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "创建成功");
        result.put("data", request);
        return result;
    }
    
    /**
     * 用户请求数据
     */
    public static class UserRequest {
        private String name;
        private int age;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public int getAge() {
            return age;
        }
        
        public void setAge(int age) {
            this.age = age;
        }
    }
} 