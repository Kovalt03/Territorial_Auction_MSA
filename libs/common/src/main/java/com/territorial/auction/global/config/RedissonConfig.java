package com.territorial.auction.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean isSslEnabled;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String scheme = isSslEnabled ? "rediss://" : "redis://";
        SingleServerConfig singleServerConfig =
                config.useSingleServer().setAddress(scheme + host + ":" + port);
        if (!password.isBlank()) {
            singleServerConfig.setPassword(password);
        }
        return Redisson.create(config);
    }
}
