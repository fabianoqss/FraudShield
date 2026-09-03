package com.fraudetection.fraud_detection_service.kafka.consumers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionCreatedConsumer {

    private static final String TOPIC = "transaction.created";

    @KafkaListener(topics = TOPIC, groupId = "transaction-service")
    public void handle(KafkaEventEnvelope envelope,  @Header(KafkaHeaders.OFFSET) long offset){

    }
}
