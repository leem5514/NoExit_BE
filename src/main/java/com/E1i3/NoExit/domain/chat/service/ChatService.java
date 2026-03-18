package com.E1i3.NoExit.domain.chat.service;

import com.E1i3.NoExit.domain.chat.domain.ChatMessageEntity;
import com.E1i3.NoExit.domain.chat.domain.ChatRoom;
import com.E1i3.NoExit.domain.chat.dto.ChatMessage;
import com.E1i3.NoExit.domain.chat.repository.ChatMessageRepository;
import com.E1i3.NoExit.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RedisMessagePublisher redisMessagePublisher;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    public List<ChatMessageEntity> getMessagesForRoom(Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        return chatMessageRepository.findByChatRoom(chatRoom);
    }

    public void handleMessage(ChatMessage chatMessage) {
        ChatRoom chatRoom = chatRoomRepository.findById(Long.parseLong(chatMessage.getRoomId()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid room ID"));

        ChatMessageEntity chatMessageEntity = ChatMessageEntity.builder()
                .sender(chatMessage.getSender())
                .senderName(chatMessage.getSenderName())
                .senderProfileImage(chatMessage.getSenderProfileImage())
                .content(chatMessage.getContent())
                .chatRoom(chatRoom)
                .timestamp(chatMessage.getTimestamp())
                .build();

        chatMessageRepository.save(chatMessageEntity);

        redisMessagePublisher.publish(chatMessage);
    }

    public void publishOnly(ChatMessage chatMessage) {
        redisMessagePublisher.publish(chatMessage);
    }
}
