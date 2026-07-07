package com.domain.room.repository;

import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomStock;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomStockRepository extends JpaRepository<RoomStock, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT rs FROM RoomStock rs WHERE rs.roomProduct.id = :roomProductId AND rs.date BETWEEN :arrDate AND :depDate")
  List<RoomStock> findByRoomProductIdAndDateBetweenWithLock(
      @Param("roomProductId") Long roomProductId,
      @Param("arrDate") LocalDate arrDate,
      @Param("depDate") LocalDate depDate);

  @Query("SELECT rs FROM RoomStock rs WHERE rs.roomProduct.id = :roomProductId AND rs.date BETWEEN :arrDate AND :depDate")
  List<RoomStock> findByRoomProductIdAndDateBetween(
      @Param("roomProductId") Long roomProductId,
      @Param("arrDate") LocalDate arrDate,
      @Param("depDate") LocalDate depDate);

  Optional<RoomStock> findByRoomProductAndDate(RoomProduct roomProduct, LocalDate date);

  void deleteByRoomProduct(RoomProduct roomProduct);
}
