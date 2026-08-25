package com.territorial.auction.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class StompChannelInterceptorTest {

    @InjectMocks private StompChannelInterceptor interceptor;

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private MessageChannel channel;

    private Message<byte[]> buildMessage(StompCommand command, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Nested
    @DisplayName("CONNECT 프레임")
    class Connect {

        @Test
        @DisplayName("토큰 없음 → Principal null 유지, 통과")
        void connect_noToken_passThrough() {
            Message<byte[]> message = buildMessage(StompCommand.CONNECT, null);

            Message<?> result = interceptor.preSend(message, channel);

            assertThat(result).isNotNull();
            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            assertThat(resultAccessor.getUser()).isNull();
            then(jwtTokenProvider).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("유효하지 않은 토큰 → IllegalArgumentException")
        void connect_invalidToken_throwException() {
            Message<byte[]> message = buildMessage(StompCommand.CONNECT, "Bearer invalid.token");
            given(jwtTokenProvider.validate("invalid.token")).willReturn(false);

            assertThatThrownBy(() -> interceptor.preSend(message, channel))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("유효하지 않은 JWT 토큰입니다.");
        }

        @Test
        @DisplayName("유효한 토큰 → Principal에 userId 주입")
        void connect_validToken_setUser() {
            Message<byte[]> message = buildMessage(StompCommand.CONNECT, "Bearer valid.token");
            given(jwtTokenProvider.validate("valid.token")).willReturn(true);
            given(jwtTokenProvider.getUserId("valid.token")).willReturn(1L);

            Message<?> result = interceptor.preSend(message, channel);

            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            assertThat(resultAccessor.getUser()).isNotNull();
            assertThat(resultAccessor.getUser().getName()).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("CONNECT 이외 프레임")
    class NonConnect {

        @Test
        @DisplayName("SEND 프레임 → JWT 검증 없이 통과")
        void send_passThrough() {
            Message<byte[]> message = buildMessage(StompCommand.SEND, "Bearer valid.token");

            Message<?> result = interceptor.preSend(message, channel);

            assertThat(result).isNotNull();
            then(jwtTokenProvider).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("SUBSCRIBE 프레임 → JWT 검증 없이 통과")
        void subscribe_passThrough() {
            Message<byte[]> message = buildMessage(StompCommand.SUBSCRIBE, null);

            Message<?> result = interceptor.preSend(message, channel);

            assertThat(result).isNotNull();
            then(jwtTokenProvider).shouldHaveNoInteractions();
        }
    }
}
