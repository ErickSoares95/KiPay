package io.github.ericksoares95.kipay.accounts.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** The civil zone is applied where the date is needed ({@code AccountHolderPolicy}); the clock stays in UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
