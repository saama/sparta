package com.domain.coupon.repository;

import com.domain.coupon.entity.UserCoupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCoupon> findByUserId(Long userId);

    @Query("SELECT uc FROM UserCoupon uc JOIN FETCH uc.coupon WHERE uc.id = :id AND uc.userId = :userId")
    Optional<UserCoupon> findByIdAndUserIdWithCoupon(@Param("id") Long id, @Param("userId") Long userId);
}
