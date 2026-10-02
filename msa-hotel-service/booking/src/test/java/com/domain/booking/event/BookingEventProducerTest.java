package com.domain.booking.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 동작 검증
 *
 * <p>DB/Kafka 없이 최소한의 Spring 컨텍스트(가짜 트랜잭션 매니저 + Mock KafkaTemplate)만 띄워
 * "커밋 전에는 전송되지 않고, 커밋 후에만 전송되며, 롤백 시에는 전송되지 않는다"를 확인한다.
 * Mockito 단위 테스트로는 트랜잭션 동기화가 동작하지 않아 이 부분을 검증할 수 없다.
 */
@SpringJUnitConfig(BookingEventProducerTest.Config.class)
@TestPropertySource(properties = {
    "app.kafka.topics.booking-events=booking-events",
    "app.kafka.topics.booking-cancelled-events=booking-cancelled-events"})
class BookingEventProducerTest {

  @Configuration
  @EnableTransactionManagement // @TransactionalEventListener 처리기(TransactionalEventListenerFactory) 등록
  static class Config {

    @Bean
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> kafkaTemplate() {
      return mock(KafkaTemplate.class);
    }

    @Bean
    BookingEventProducer bookingEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
      return new BookingEventProducer(kafkaTemplate);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new NoOpTransactionManager();
    }
  }

  /** 실제 DB 없이 트랜잭션 동기화(커밋/롤백 콜백)만 동작시키는 테스트용 트랜잭션 매니저 */
  static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
    }
  }

  @Autowired
  private ApplicationEventPublisher eventPublisher;
  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired
  private PlatformTransactionManager transactionManager;

  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    reset(kafkaTemplate);
    // send() 결과에 whenComplete를 체이닝하므로 Future를 반환하도록 스텁
    given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());
    transactionTemplate = new TransactionTemplate(transactionManager);
  }

  private BookingCreatedEvent createEvent() {
    return BookingCreatedEvent.builder().bookingId(1L).bookingNumber("BK001").build();
  }

  @Test
  @DisplayName("트랜잭션 커밋 이후에만 Kafka로 전송된다")
  void publish_afterCommit() {
    BookingCreatedEvent event = createEvent();

    transactionTemplate.executeWithoutResult(status -> {
      eventPublisher.publishEvent(event);
      // 아직 커밋 전이므로 전송되지 않아야 한다
      verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    });

    // 커밋 완료 후 전송
    verify(kafkaTemplate).send(eq("booking-events"), eq("1"), eq(event));
  }

  @Test
  @DisplayName("트랜잭션이 롤백되면 Kafka로 전송되지 않는다")
  void notPublish_onRollback() {
    transactionTemplate.executeWithoutResult(status -> {
      eventPublisher.publishEvent(createEvent());
      status.setRollbackOnly(); // 예약 생성 중 예외 등으로 롤백되는 상황
    });

    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("예약 취소 이벤트도 커밋 이후 booking-cancelled-events 토픽으로 전송된다")
  void publishCancelled_afterCommit() {
    BookingCancelledEvent event = BookingCancelledEvent.builder().bookingId(2L).build();

    transactionTemplate.executeWithoutResult(status -> {
      eventPublisher.publishEvent(event);
      verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    });

    verify(kafkaTemplate).send(eq("booking-cancelled-events"), eq("2"), eq(event));
  }

  @Test
  @DisplayName("예약 취소가 롤백되면 취소 이벤트도 전송되지 않는다 (유효한 예약의 결제가 환불되는 사고 방지)")
  void notPublishCancelled_onRollback() {
    transactionTemplate.executeWithoutResult(status -> {
      eventPublisher.publishEvent(BookingCancelledEvent.builder().bookingId(2L).build());
      status.setRollbackOnly();
    });

    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("트랜잭션 밖에서 발행된 이벤트는 전송되지 않는다 (fallbackExecution = false)")
  void notPublish_withoutTransaction() {
    eventPublisher.publishEvent(createEvent());

    verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
  }
}
