package com.territorial.auction.global.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    // 서버 LocalDateTime은 KST(UTC+9) 기준으로 운영된다(단일 타임존, DST 없음). 직렬화 시 실제
    // 오프셋 +09:00 을 붙여 클라이언트가 정확한 시각으로 파싱하도록 통일한다.
    // (과거 'Z'를 붙이면 KST 시각이 UTC로 오인돼 클라이언트가 +9h 만큼 부풀려 표시하던 문제 수정.)
    private static final DateTimeFormatter KST_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss+09:00");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeKstCustomizer() {
        return builder ->
                builder.serializerByType(
                        LocalDateTime.class, new LocalDateTimeSerializer(KST_FORMAT));
    }
}
