package com.employeehub.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Injectable clock so timestamps are deterministic in unit tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
