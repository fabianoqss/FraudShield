package com.fraudetection.notification_service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    /*
     * The raw producer used for poison-pill DLT publishing is built here as a plain
     * object, not a separate @Bean: KafkaAutoConfiguration#kafkaTemplate() is guarded
     * by @ConditionalOnMissingBean(KafkaTemplate.class) (matched by raw type, ignoring
     * generics), so registering any other KafkaTemplate bean here would silently
     * suppress Boot's own default kafkaTemplate bean.
     * Key stays a String serializer: a poison-pill only fails VALUE deserialization
     * (spring.deserializer.key.delegate.class is StringDeserializer and always
     * succeeds), so DeadLetterPublishingRecoverer republishes the already-deserialized
     * String key alongside the undecoded value bytes — a byte[] key serializer here
     * would throw a ClassCastException on that String.
     */
    private KafkaTemplate<String, byte[]> buildDltRawKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> configs = new HashMap<>(kafkaProperties.buildProducerProperties());
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configs));
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                                  KafkaProperties kafkaProperties) {
        Map<Class<?>, KafkaOperations<?, ?>> recovererTemplates = new HashMap<>();
        recovererTemplates.put(byte[].class, buildDltRawKafkaTemplate(kafkaProperties));
        recovererTemplates.put(Object.class, kafkaTemplate);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(recovererTemplates);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(DeserializationException.class);
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
