package com.domain.room.service;

import com.domain.room.dto.request.RoomProductCreateRequest;
import com.domain.room.dto.request.RoomStockInitRequest;
import com.domain.room.dto.response.AvailableRoomResponse;
import com.domain.room.dto.response.RoomProductResponse;
import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomStock;
import com.domain.room.repository.RoomProductRepository;
import com.domain.room.repository.RoomStockRepository;
import com.global.exception.DomainException;
import com.global.exception.DomainExceptionCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomProductService {

  private final RoomProductRepository roomProductRepository;
  private final RoomStockRepository roomStockRepository;

  @Transactional(readOnly = true)
  public List<AvailableRoomResponse> getAvailableRooms(LocalDate arrDate, LocalDate depDate,
      int guestCount) {
    List<RoomProduct> rooms = roomProductRepository.findAll();
    List<AvailableRoomResponse> result = new ArrayList<>();

    for (RoomProduct room : rooms) {
      if (!room.getIsActive() || room.getMaxCapacity() < guestCount) {
        continue;
      }
      List<RoomStock> stocks = roomStockRepository.findByRoomProductIdAndDateBetween(
          room.getId(), arrDate, depDate.minusDays(1));

      if (stocks.isEmpty()) {
        continue;
      }
      int minStock = stocks.stream().mapToInt(RoomStock::getStock).min().orElse(0);
      if (minStock > 0) {
        result.add(AvailableRoomResponse.of(room, minStock));
      }
    }
    return result;
  }

  @Transactional(readOnly = true)
  public RoomProductResponse getRoom(Long id) {
    RoomProduct room = roomProductRepository.findById(id)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_ROOM));
    return RoomProductResponse.from(room);
  }

  @Transactional
  public RoomProductResponse createRoom(RoomProductCreateRequest request) {
    RoomProduct room = RoomProduct.builder()
        .name(request.getName())
        .roomType(request.getRoomType())
        .price(request.getPrice())
        .baseCapacity(request.getBaseCapacity())
        .maxCapacity(request.getMaxCapacity())
        .description(request.getDescription())
        .thumbnailUrl(request.getThumbnailUrl())
        .build();
    return RoomProductResponse.from(roomProductRepository.save(room));
  }

  @Transactional
  public RoomProductResponse updateRoom(Long id, RoomProductCreateRequest request) {
    RoomProduct room = roomProductRepository.findById(id)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_ROOM));
    room.update(request.getName(), request.getRoomType(), request.getPrice(),
        request.getBaseCapacity(), request.getMaxCapacity(), request.getDescription(),
        request.getThumbnailUrl());
    return RoomProductResponse.from(room);
  }

  @Transactional
  public void deleteRoom(Long id) {
    RoomProduct room = roomProductRepository.findById(id)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_ROOM));
    room.deactivate();
  }

  @Transactional
  public void initStock(Long roomId, RoomStockInitRequest request) {
    RoomProduct room = roomProductRepository.findById(roomId)
        .orElseThrow(() -> new DomainException(DomainExceptionCode.NOT_FOUND_ROOM));

    roomStockRepository.deleteByRoomProduct(room);

    List<RoomStock> stocks = new ArrayList<>();
    LocalDate date = request.getStartDate();
    while (!date.isAfter(request.getEndDate())) {
      stocks.add(RoomStock.builder()
          .roomProduct(room)
          .date(date)
          .stock(request.getStock())
          .build());
      date = date.plusDays(1);
    }
    roomStockRepository.saveAll(stocks);
  }
}
