package com.E1i3.NoExit.domain.chat.service;

import com.E1i3.NoExit.domain.chat.dto.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisMessagePublisher {

    @Qualifier("chatRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    private static final String CHAT_CHANNEL = "chatroom";

    public void publish(ChatMessage chatMessage) {
        try {
            String payload = objectMapper.writeValueAsString(chatMessage);
            redisTemplate.convertAndSend(CHAT_CHANNEL, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to publish chat message", e);
        }
    }
}
