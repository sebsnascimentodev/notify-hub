package tech.cuia.notifyhub.infrastructure.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    // Constantes centralizadas: evita strings mágicas espalhadas pelo codebase
    public static final String TOPIC_PENDING = "notifications.pending";
    public static final String TOPIC_RETRY   = "notifications.retry";
    public static final String TOPIC_DLQ     = "notifications.dlq";

    public static final String HEADER_RETRY_AFTER = "notification-retry-after";

    // -------------------------------------------------------------------------
    // Tópicos — criados automaticamente na inicialização via AdminClient
    // -------------------------------------------------------------------------

    @Bean
    public org.apache.kafka.clients.admin.NewTopic pendingTopic() {
        return TopicBuilder.name(TOPIC_PENDING).partitions(3).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic retryTopic() {
        // Partição única no retry: garante ordem de processamento por notificação
        return TopicBuilder.name(TOPIC_RETRY).partitions(3).replicas(1).build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic dlqTopic() {
        return TopicBuilder.name(TOPIC_DLQ).partitions(1).replicas(1).build();
    }

    // -------------------------------------------------------------------------
    // Consumer factory tipada para NotificationEvent
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, NotificationEvent> notificationConsumerFactory(
            KafkaProperties kafkaProperties) {
        var props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "tech.cuia.notifyhub.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // -------------------------------------------------------------------------
    // Container factories — MANUAL_IMMEDIATE: offset só é commitado após ack()
    // explícito, garantindo at-least-once delivery mesmo em caso de crash mid-flight.
    // -------------------------------------------------------------------------

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, NotificationEvent> notificationConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>();
        factory.setConsumerFactory(notificationConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3); // 1 thread por partição do tópico pending
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
    dlqListenerContainerFactory(
            ConsumerFactory<String, NotificationEvent> notificationConsumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>();
        factory.setConsumerFactory(notificationConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(1);
        return factory;
    }
}
