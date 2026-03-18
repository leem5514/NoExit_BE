package com.E1i3.NoExit.domain.chat.controller;

import com.E1i3.NoExit.domain.chat.domain.ChatMessageEntity;
import com.E1i3.NoExit.domain.chat.dto.ChatMessage;
import com.E1i3.NoExit.domain.chat.service.ChatService;
import com.E1i3.NoExit.domain.member.domain.Member;
import com.E1i3.NoExit.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final MemberRepository memberRepository;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(ChatMessage chatMessage, StompHeaderAccessor accessor) {
        String senderEmail = accessor.getUser().getName();

        Member member = memberRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + senderEmail));

        chatMessage.setSender(senderEmail);
        chatMessage.setSenderName(member.getNickname());
        chatMessage.setSenderProfileImage(member.getProfileImage());
        chatMessage.setTimestamp(System.currentTimeMillis());
        chatMessage.setType(ChatMessage.MessageType.CHAT);

        chatService.handleMessage(chatMessage);
    }

    @MessageMapping("/chat.join")
    public void joinRoom(ChatMessage chatMessage, StompHeaderAccessor accessor) {
        String senderEmail = accessor.getUser().getName();

        Member member = memberRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + senderEmail));

        chatMessage.setSender(senderEmail);
        chatMessage.setSenderName(member.getNickname());
        chatMessage.setSenderProfileImage(member.getProfileImage());
        chatMessage.setTimestamp(System.currentTimeMillis());
        chatMessage.setType(ChatMessage.MessageType.JOIN);
        chatMessage.setContent(member.getNickname() + " 님이 방에 참가하셨습니다!");

        chatService.publishOnly(chatMessage);
    }

    @GetMapping("/chat/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageEntity>> getMessages(@PathVariable String roomId) {
        List<ChatMessageEntity> messages = chatService.getMessagesForRoom(Long.parseLong(roomId));
        return ResponseEntity.ok(messages);
    }
}
