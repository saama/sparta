package com.global.config;

import com.global.exception.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;

/**
 * Kafka Consumer 에러 처리 설정 (재시도 + Dead Letter Topic)
 *
 * <p>리스너에서 예외가 발생하면 아래 순서로 처리한다.
 * <ol>
 *   <li>재시도 가능한 예외(일시적인 DB 장애 등): 1초 → 2초 → 4초 간격으로 최대 3회 재시도</li>
 *   <li>재시도 불가능한 예외({@link DomainException} 같은 비즈니스 예외, 역직렬화 실패): 즉시 DLT로 이동</li>
 *   <li>재시도를 모두 소진한 경우: {@code <원본 토픽>.DLT} 토픽으로 메시지를 옮기고 offset 커밋</li>
 * </ol>
 * 이렇게 하면 실패한 메시지 하나 때문에 파티션 전체가 멈추거나(무한 재시도) 메시지가 조용히 유실되지 않는다.
 * DLT에 쌓인 메시지는 원인 해결 후 재처리(replay)하거나 운영자가 확인한다.
 *
 * <p>Spring Boot는 {@code CommonErrorHandler} 타입 빈이 있으면 자동으로 리스너 컨테이너 팩토리에 등록한다.
 *
 * <p>참고: 여기서 사용하는 재시도는 컨슈머 스레드 안에서 대기하는 "블로킹 재시도"라서,
 * 재시도하는 동안 같은 파티션의 다음 메시지는 처리되지 않는다(대신 순서는 보장된다).
 * 재시도 중에도 뒤의 메시지를 계속 처리해야 한다면 {@code @RetryableTopic}(논블로킹 재시도)을 검토한다.
 */
@Slf4j
@Configuration
public class KafkaErrorHandlerConfig {

    /** DLT 토픽 접미사: payment-completed-events → payment-completed-events.DLT */
    public static final String DLT_SUFFIX = ".DLT";

    /** 첫 재시도 대기 시간 */
    private static final long INITIAL_INTERVAL_MS = 1_000L;
    /** 재시도마다 대기 시간 배수 (1초 → 2초 → 4초) */
    private static final double MULTIPLIER = 2.0;
    /** 최대 재시도 횟수 (최초 시도 제외) */
    private static final int MAX_RETRIES = 3;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
        ProducerFactory<?, ?> producerFactory) {

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);

        return createErrorHandler(deadLetterPublishingRecoverer(kafkaTemplate, producerFactory), backOff);
    }

    /**
     * 에러 핸들러 생성 (재시도 정책 + 예외 분류)
     *
     * <p>단위 테스트에서 대기 시간 없는 BackOff와 Mock Recoverer로 같은 정책을 검증할 수 있도록 분리했다.
     */
    static DefaultErrorHandler createErrorHandler(ConsumerRecordRecoverer recoverer, BackOff backOff) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, ex) -> {
            // DLT로 보내기 직전에 ERROR 로그를 남겨 ELK에서 추적할 수 있게 한다.
            log.error("Kafka 메시지 처리 최종 실패 → DLT 이동 - topic: {}, partition: {}, offset: {}, key: {}",
                record.topic(), record.partition(), record.offset(), record.key(), ex);
            recoverer.accept(record, ex);
        }, backOff);

        // 비즈니스 예외는 몇 번을 다시 시도해도 결과가 같으므로 재시도 없이 바로 DLT로 보낸다.
        // (역직렬화 실패 등 프레임워크 예외는 DefaultErrorHandler 기본값으로 이미 재시도 제외)
        errorHandler.addNotRetryableExceptions(DomainException.class);

        // 재시도 중임을 WARN 로그로 남긴다 (몇 번째 시도에서 실패했는지 추적용)
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
            log.warn("Kafka 메시지 처리 실패, 재시도 예정 - topic: {}, offset: {}, attempt: {}, cause: {}",
                record.topic(), record.offset(), deliveryAttempt, ex.getMessage()));

        // DLT로 옮긴(복구한) 메시지의 offset을 즉시 커밋한다. (ack-mode가 MANUAL_IMMEDIATE일 때 동작)
        // 커밋하지 않으면 재시작 시 같은 메시지를 다시 받아 DLT에 중복으로 쌓일 수 있다.
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    /**
     * 실패 메시지를 DLT로 발행하는 Recoverer
     *
     * <p>DLT 파티션을 -1로 지정해 원본 key 기준으로 Kafka가 파티션을 정하게 한다.
     * (원본 파티션 번호를 그대로 쓰면 DLT 토픽의 파티션 수가 원본보다 적을 때 발행이 실패한다)
     *
     * <p>역직렬화에 실패한 메시지는 객체가 아닌 원본 byte[]로 전달되므로,
     * JSON 직렬화 템플릿 대신 byte[]를 그대로 보내는 템플릿을 사용해야 원본 데이터가 보존된다.
     */
    private DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
        KafkaTemplate<String, Object> kafkaTemplate, ProducerFactory<?, ?> producerFactory) {

        // 주의: 빈으로 등록하면 Spring Boot의 기본 KafkaTemplate 자동 구성이 비활성화되므로 내부 객체로만 생성한다.
        KafkaTemplate<String, byte[]> bytesTemplate = new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(producerFactory.getConfigurationProperties(),
                new StringSerializer(), new ByteArraySerializer()));

        // 값 타입별로 사용할 템플릿 지정 (순서대로 매칭되므로 byte[]를 먼저 둔다)
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, kafkaTemplate);

        return new DeadLetterPublishingRecoverer(templates,
            (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));
    }
}
