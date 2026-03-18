package com.E1i3.NoExit.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }
    private MessageType type;
    private String content;
    private String sender;
    private String roomId;

    private String senderName; // 사용자 이름 추가
    private String senderProfileImage; // 프로필 이미지 추가
    private Long timestamp;
}

