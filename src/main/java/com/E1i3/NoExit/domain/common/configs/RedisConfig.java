package com.E1i3.NoExit.domain.common.configs;

import com.E1i3.NoExit.domain.chat.service.RedisMessageSubscriber;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

	@Value("${spring.redis.host}")
	private String host;

	@Value("${spring.redis.port}")
	private int port;

    public LettuceConnectionFactory redisConnectionFactory(int index) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setDatabase(index);
        return new LettuceConnectionFactory(config);
    }


	// 이메일 인증 (0번 데이터베이스)
	@Bean
	@Primary
	LettuceConnectionFactory connectionFactoryEmail() {
		return redisConnectionFactory(0);
	}

	@Bean
	@Primary
	public RedisTemplate<String, Object> redisTemplate() {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactoryEmail());
		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setValueSerializer(new StringRedisSerializer());

		return redisTemplate;
	}

	// 예약 시스템 (1번 데이터베이스)
	@Bean
	@Qualifier("2")
	LettuceConnectionFactory connectionFactoryReservation() {
		return redisConnectionFactory(1);
	}

	@Bean
	@Qualifier("2")
	public RedisTemplate<String, Object> redisTemplate1() {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactoryReservation());
		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());

		return redisTemplate;
	}

	// 	알림 서비스 (2번 데이터베이스)
	@Bean
	@Qualifier("3")
	LettuceConnectionFactory connectionFactoryNotification() {
		return redisConnectionFactory(2);
	}

	@Bean
	@Qualifier("3")
	public RedisTemplate<String, Object> redisTemplate2() {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactoryNotification());
		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());

		return redisTemplate;
	}

	@Bean
	@Qualifier("3")
	public RedisMessageListenerContainer redisMessageListenerContainer(@Qualifier("3") RedisConnectionFactory sseFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(sseFactory);
		return container;
	}

	// 	게시글 좋아요 (3번 데이터베이스)
	@Bean
	@Qualifier("4")
	LettuceConnectionFactory connectionFactoryBoardLike() {
		return redisConnectionFactory(3);
	}

	@Bean
	@Qualifier("4")
	public RedisTemplate<String, Object> redisTemplate3() {
	    RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
	    redisTemplate.setConnectionFactory(connectionFactoryBoardLike());
	    redisTemplate.setKeySerializer(new StringRedisSerializer());
	    redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
	    return redisTemplate;
	}
	// 	댓글 좋아요 (4번 데이터베이스)
	@Bean
	@Qualifier("5")
	LettuceConnectionFactory connectionFactoryCommentLike() {
		return redisConnectionFactory(4);
	}

	@Bean
	@Qualifier("5")
	public RedisTemplate<String, Object> redisTemplate4() {
	    RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
	    redisTemplate.setConnectionFactory(connectionFactoryCommentLike());
	    redisTemplate.setKeySerializer(new StringRedisSerializer());
	    redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
	    return redisTemplate;
	}

	// 채팅 서비스 (5번 데이터베이스
    @Bean
    @Qualifier("chatRedisConnectionFactory")
    public LettuceConnectionFactory connectionFactoryChat() {
        return redisConnectionFactory(5);
    }

    @Bean
    @Qualifier("chatRedisTemplate")
    public RedisTemplate<String, Object> redisTemplateChat() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactoryChat());
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

	@Bean
	@Qualifier("chatTopic")
	public ChannelTopic topic() {
		return new ChannelTopic("chatroom");
	}

    @Bean
    @Qualifier("chatRedisMessageListenerContainer")
    public RedisMessageListenerContainer chatRedisMessageListenerContainer(
            @Qualifier("chatRedisConnectionFactory") RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber redisMessageSubscriber,
            @Qualifier("chatTopic") ChannelTopic chatTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisMessageSubscriber, chatTopic);
        return container;
    }

//	@Bean
//	@Qualifier("6")
//	LettuceConnectionFactory connectionFactoryChat() {
//		return redisConnectionFactory(5);
//	}
//
//	@Bean
//	@Qualifier("6")
//	public RedisTemplate<String, Object> redisTemplateChat() {
//		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
//		redisTemplate.setConnectionFactory(connectionFactoryChat());
//		redisTemplate.setKeySerializer(new StringRedisSerializer());
//		redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
//
//		return redisTemplate;
//	}
//
//	// Redis Message Listener for Chat
//	@Bean
//	public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactoryChat) {
//		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
//		container.setConnectionFactory(connectionFactoryChat);
//		return container;
//	}
//
//	@Bean
//	public ChannelTopic topic() {
//		return new ChannelTopic("chatroom");
//	}

}
