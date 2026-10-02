package com.domain.booking.repository;

import com.domain.booking.entity.Booking;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

  List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<Booking> findByIdAndUserId(Long id, Long userId);

  Optional<Booking> findByBookingNumber(String bookingNumber);

  // 결제 완료 이벤트 처리용: SELECT ... FOR UPDATE 로 최신 커밋 상태를 읽고 행을 잠근다.
  // 동시에 진행 중인 예약/결제 취소와 순서를 직렬화해 상태 덮어쓰기(Lost Update)를 방지한다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Booking b WHERE b.id = :id")
  Optional<Booking> findByIdWithLock(@Param("id") Long id);
}
