package com.susume.recommendation.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.susume.recommendation.config.RabbitMQConfig;
import com.susume.recommendation.dto.CreateItemRequest;
import com.susume.recommendation.dto.ItemCreatedEvent;

@Service
public class ItemEventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public ItemEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ItemCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ITEM_CREATED_QUEUE,
                event);
    }
}
