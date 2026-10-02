package com.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 에러 핸들러 정책 검증
 *
 * <p>실제 운영 설정과 같은 {@code createErrorHandler()}를 사용하되,
 * 테스트가 느려지지 않도록 대기 시간 0의 BackOff(최대 2회 재시도)와 Mock Recoverer를 주입한다.
 * 리스너 컨테이너가 예외를 받으면 호출하는 {@code handleRemaining()}을 직접 호출해 동작을 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaErrorHandlerConfigTest {

  private static final int MAX_RETRIES = 2;

  @Mock
  private ConsumerRecordRecoverer recoverer;
  @Mock
  private Consumer<?, ?> consumer;
  @Mock
  private MessageListenerContainer container;

  private DefaultErrorHandler errorHandler;
  private ConsumerRecord<String, Object> record;

  @BeforeEach
  void setUp() {
    errorHandler = KafkaErrorHandlerConfig.createErrorHandler(
        recoverer, new FixedBackOff(0L, MAX_RETRIES));
    record = new ConsumerRecord<>("payment-completed-events", 0, 10L, "1", "payload");

    // 운영 설정과 동일하게 MANUAL_IMMEDIATE (commitRecovered 동작 조건)
    ContainerProperties containerProperties = new ContainerProperties("payment-completed-events");
    containerProperties.setAckMode(AckMode.MANUAL_IMMEDIATE);
    given(container.getContainerProperties()).willReturn(containerProperties);
    given(container.isRunning()).willReturn(true);
  }

  /** 리스너가 던진 예외는 컨테이너에서 ListenerExecutionFailedException으로 감싸져 전달된다 */
  private void handle(Exception cause) {
    List<ConsumerRecord<?, ?>> records = new ArrayList<>(List.of(record));
    errorHandler.handleRemaining(
        new ListenerExecutionFailedException("listener failed", cause), records, consumer, container);
  }

  @Test
  @DisplayName("비즈니스 예외(DomainException)는 재시도 없이 즉시 DLT로 이동")
  void domainException_notRetried() {
    handle(new DomainException(DomainExceptionCode.NOT_FOUND_BOOKING));

    // 첫 실패에서 바로 recoverer(DLT 발행) 호출
    verify(recoverer).accept(eq(record), any());
    // DLT로 옮긴 메시지의 offset 커밋
    verify(consumer).commitSync(anyMap(), any());
  }

  @Test
  @DisplayName("일시적 예외는 재시도 횟수를 모두 소진한 뒤에 DLT로 이동")
  void transientException_retriedThenRecovered() {
    RuntimeException transientError = new RuntimeException("DB connection timeout");

    // 최초 시도 + 재시도 2회까지는 DLT로 보내지 않고 같은 offset으로 seek하여 다시 받도록 한다.
    // 이때 핸들러는 "아직 재시도 중"임을 컨테이너에 알리기 위해 RecordInRetryException을 던진다.
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      assertThatThrownBy(() -> handle(transientError))
          .isInstanceOf(RuntimeException.class) // 실제 타입은 package-private인 RecordInRetryException
          .hasMessageContaining("Record in retry and not yet recovered");
      verify(recoverer, never()).accept(any(), any());
    }

    // 재시도 소진 → DLT 이동
    handle(transientError);
    verify(recoverer).accept(eq(record), any());
  }

  @Test
  @DisplayName("DLT 접미사는 .DLT")
  void dltSuffix() {
    assertThat("payment-completed-events" + KafkaErrorHandlerConfig.DLT_SUFFIX)
        .isEqualTo("payment-completed-events.DLT");
  }
}
