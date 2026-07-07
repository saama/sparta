package com.domain.booking.repository;

import com.domain.booking.entity.Booking;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

  List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<Booking> findByIdAndUserId(Long id, Long userId);

  Optional<Booking> findByBookingNumber(String bookingNumber);
}
