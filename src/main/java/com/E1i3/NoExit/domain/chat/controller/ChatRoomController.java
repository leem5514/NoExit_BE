package com.E1i3.NoExit.domain.chat.controller;

import com.E1i3.NoExit.domain.chat.domain.ChatRoom;
import com.E1i3.NoExit.domain.chat.dto.CreateRoomRequest;
import com.E1i3.NoExit.domain.chat.service.ChatRoomService;
import com.E1i3.NoExit.domain.chat.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatService chatService;

    public ChatRoomController(ChatRoomService chatRoomService, ChatService chatService) {
        this.chatRoomService = chatRoomService;
        this.chatService = chatService;
    }

    @PostMapping("/createRoom")
    public ResponseEntity<ChatRoom> createRoom(@RequestBody CreateRoomRequest request) {
        ChatRoom chatRoom = chatRoomService.createRoom(request.getName(), request.getPassword());
        return ResponseEntity.ok(chatRoom);
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> roomList() {
        return ResponseEntity.ok(chatRoomService.findAllRooms());
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoom> getRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(chatRoomService.findRoomById(roomId));
    }

    @GetMapping("/myrooms")
    public ResponseEntity<List<ChatRoom>> getMyChatRooms() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<ChatRoom> chatRooms = chatRoomService.getChatRoomsForMember(email);
        return ResponseEntity.ok(chatRooms);
    }
}
