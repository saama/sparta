package com.domain.room.repository;

import com.domain.room.entity.RoomProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomProductRepository extends JpaRepository<RoomProduct, Long> {

}
