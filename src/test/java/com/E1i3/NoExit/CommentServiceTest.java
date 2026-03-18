package com.E1i3.NoExit.domain.comment.service;

import com.E1i3.NoExit.domain.board.repository.BoardRepository;
import com.E1i3.NoExit.domain.comment.domain.Comment;
import com.E1i3.NoExit.domain.comment.repository.CommentRepository;
import com.E1i3.NoExit.domain.member.domain.Member;
import com.E1i3.NoExit.domain.member.repository.MemberRepository;
import com.E1i3.NoExit.domain.notification.controller.SseController;
import com.E1i3.NoExit.domain.notification.repository.NotificationRepository;
import com.E1i3.NoExit.domain.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private SseController sseController;
    @Mock
    private RedisTemplate<String, Object> commentRedisTemplate;

    @InjectMocks
    private CommentService commentService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void commentDelete_throws_when_not_owner() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("other@test.com", null)
        );

        Member loginMember = Member.builder()
                .email("other@test.com")
                .build();

        Member owner = Member.builder()
                .email("owner@test.com")
                .build();

        Comment comment = Comment.builder()
                .member(owner)
                .contents("hello")
                .build();

        when(memberRepository.findByEmail("other@test.com")).thenReturn(Optional.of(loginMember));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertThrows(IllegalArgumentException.class, () -> commentService.commentDelete(1L));
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void commentDelete_success_when_owner() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@test.com", null)
        );

        Member owner = Member.builder()
                .email("owner@test.com")
                .build();

        Comment comment = Comment.builder()
                .member(owner)
                .contents("hello")
                .build();

        when(memberRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(owner));
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        assertDoesNotThrow(() -> commentService.commentDelete(1L));
        verify(commentRepository, times(1)).save(comment);
    }

    @Test
    void commentDelete_throws_when_comment_not_found() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@test.com", null)
        );

        Member owner = Member.builder()
                .email("owner@test.com")
                .build();

        when(memberRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(owner));
        when(commentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> commentService.commentDelete(1L));
    }
}
