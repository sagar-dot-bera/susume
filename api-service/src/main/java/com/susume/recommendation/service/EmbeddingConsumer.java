package com.susume.recommendation.service;

import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.susume.recommendation.client.EmbeddingServiceClient;
import com.susume.recommendation.config.RabbitMQConfig;
import com.susume.recommendation.dto.CreateItemRequest;
import com.susume.recommendation.dto.EmbedResponse;
import com.susume.recommendation.dto.ItemCreatedEvent;
import com.susume.recommendation.entity.EmbeddingStatus;
import com.susume.recommendation.entity.Item;
import com.susume.recommendation.util.MetadataConcatenator;

@Component
public class EmbeddingConsumer {
    private final ItemService itemService;
    private final EmbeddingServiceClient embeddingServiceClient;

    public EmbeddingConsumer(ItemService itemService, EmbeddingServiceClient embeddingServiceClient) {
        this.itemService = itemService;
        this.embeddingServiceClient = embeddingServiceClient;
    }

    @RabbitListener(queues = RabbitMQConfig.ITEM_CREATED_QUEUE)
    public void process(ItemCreatedEvent event) {

        Item itemToProcess = itemService.fetchItemById(event.id());

        String text = MetadataConcatenator.concatenate(itemToProcess.getMetadata());

        float[] response = embeddingServiceClient.getEmbedding(text);

        itemService.updateEmbedding(itemToProcess.getId().toString(), EmbeddingStatus.COMPLETED, response);
    }
}
