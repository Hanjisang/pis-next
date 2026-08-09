package com.hanjisang.pis.integration.gateway;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.hanjisang.pis.integration.gateway.IntegrationEnvelope.Direction;

@Component
public class IntegrationMessageMapper {

    public IntegrationEnvelope map(IntegrationRequestDto dto) {
        if (dto == null) throw new IllegalArgumentException("集成请求不能为空");
        return new IntegrationEnvelope(required(dto.hospitalProfileCode(), "医院 Profile 不能为空"),
                enumValue(Direction.class, dto.directionCode(), "接口方向不受支持"),
                required(dto.sourceSystemCode(), "来源系统不能为空"),
                required(dto.targetSystemCode(), "目标系统不能为空"),
                required(dto.messageId(), "消息 ID 不能为空"),
                enumValue(IntegrationCapability.class, dto.capabilityCode(), "接口能力不受支持"),
                required(dto.businessKey(), "业务键不能为空"),
                required(dto.requestReference(), "脱敏请求引用不能为空"),
                required(dto.requestDigest(), "请求摘要不能为空"),
                dto.externalOccurredAt() == null ? Instant.now() : dto.externalOccurredAt());
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String message) {
        try {
            return Enum.valueOf(type, required(value, message));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(message + "：" + value, exception);
        }
    }
}
