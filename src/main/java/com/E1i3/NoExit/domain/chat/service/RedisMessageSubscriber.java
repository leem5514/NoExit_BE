package com.E1i3.NoExit.domain.chat.service;

import com.E1i3.NoExit.domain.chat.dto.ChatMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;


// 발행한 메세지 수신, websocket을 통하여 클라이언트 전달
@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());

        try {
            ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
            String destination = "/topic/room/" + chatMessage.getRoomId();
            messagingTemplate.convertAndSend(destination, chatMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize chat message", e);
        }
    }
}
