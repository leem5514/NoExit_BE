package com.E1i3.NoExit.domain.mail.service;

import com.E1i3.NoExit.domain.common.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailVerifyServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RedisService redisService;

    @InjectMocks
    private MailVerifyService mailVerifyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailVerifyService, "authCodeExpirationMillis", 300000L);
    }

    @Test
    void sendCodeToEmail_success() {
        String email = "user@test.com";

        when(redisService.getValues("EMAIL_CERTIFICATE : " + email)).thenReturn("false");
        when(redisService.checkExistsValue("false")).thenReturn(false);

        mailVerifyService.sendCodeToEmail(email);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService).setValues(eq("USER_AUTH_CODE : " + email), codeCaptor.capture(), any(Duration.class));

        String savedCode = codeCaptor.getValue();
        assertNotNull(savedCode);
        assertTrue(savedCode.matches("\\d{6}"));
    }

    @Test
    void verifiedCode_success() {
        String email = "user@test.com";
        String code = "123456";

        when(redisService.getValues("USER_AUTH_CODE : " + email)).thenReturn(code);
        when(redisService.checkExistsValue(code)).thenReturn(true);

        boolean result = mailVerifyService.verifiedCode(email, code);

        assertTrue(result);
        verify(redisService).setValues(eq("EMAIL_CERTIFICATE : " + email), eq("true"), any(Duration.class));
        verify(redisService).deleteValues("USER_AUTH_CODE : " + email);
    }

    @Test
    void verifiedCode_fail() {
        String email = "user@test.com";

        when(redisService.getValues("USER_AUTH_CODE : " + email)).thenReturn("654321");
        when(redisService.checkExistsValue("654321")).thenReturn(true);

        boolean result = mailVerifyService.verifiedCode(email, "123456");

        assertFalse(result);
        verify(redisService, never()).deleteValues(anyString());
    }
}
