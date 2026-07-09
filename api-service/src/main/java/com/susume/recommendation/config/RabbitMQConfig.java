package com.susume.recommendation.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String ITEM_CREATED_QUEUE = "item.created";

    @Bean
    public Queue itemCreatedQueue() {
        return new Queue(ITEM_CREATED_QUEUE, true);
    }
}
