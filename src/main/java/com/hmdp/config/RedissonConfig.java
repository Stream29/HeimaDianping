package com.hmdp.config;

import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public @NotNull RedissonClient instance() {
        val config = new Config();
        config.useSingleServer()
                .setAddress("TODO()")
                .setPassword("TODO()");
        return Redisson.create(config);
    }
}
