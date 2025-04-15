package com.oem.log.core.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oem.log.core.model.ApiLog;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * JSON格式日志序列化实现
 */
@Slf4j
public class JsonLogSerializer implements LogSerializer {
    
    private final ObjectMapper objectMapper;
    
    public JsonLogSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public ByteBuffer serialize(ApiLog apiLog) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(apiLog);
            int totalSize = 4 + jsonBytes.length; // 4字节存储长度 + JSON内容长度
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(totalSize);
            buffer.putInt(jsonBytes.length);
            buffer.put(jsonBytes);
            buffer.flip();
            return buffer;
        } catch (Exception e) {
            log.error("序列化日志失败", e);
            throw new RuntimeException("序列化日志失败", e);
        }
    }
    
    @Override
    public ApiLog deserialize(ByteBuffer buffer) {
        try {
            int jsonLength = buffer.getInt();
            byte[] jsonBytes = new byte[jsonLength];
            buffer.get(jsonBytes);
            return objectMapper.readValue(jsonBytes, ApiLog.class);
        } catch (Exception e) {
            log.error("反序列化日志失败", e);
            throw new RuntimeException("反序列化日志失败", e);
        }
    }
    
    @Override
    public int getFixedRecordSize() {
        return -1; // JSON是变长的
    }
} 