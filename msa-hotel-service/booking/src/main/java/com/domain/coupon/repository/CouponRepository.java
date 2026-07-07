package com.domain.coupon.repository;

import com.domain.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdWithOptimisticLock(@Param("id") Long id);
}
