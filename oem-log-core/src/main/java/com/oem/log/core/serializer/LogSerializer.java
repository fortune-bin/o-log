package com.oem.log.core.serializer;

import com.oem.log.core.model.ApiLog;

import java.nio.ByteBuffer;

/**
 * 日志序列化接口
 */
public interface LogSerializer {
    
    /**
     * 将ApiLog对象序列化为ByteBuffer
     * @param log API日志对象
     * @return 序列化后的ByteBuffer
     */
    ByteBuffer serialize(ApiLog log);
    
    /**
     * 从ByteBuffer反序列化为ApiLog对象
     * @param buffer 序列化的ByteBuffer
     * @return API日志对象
     */
    ApiLog deserialize(ByteBuffer buffer);
    
    /**
     * 获取每条日志的固定长度（用于映射文件）
     * 如果是变长的，返回-1
     * @return 每条日志的字节长度
     */
    int getFixedRecordSize();
} 