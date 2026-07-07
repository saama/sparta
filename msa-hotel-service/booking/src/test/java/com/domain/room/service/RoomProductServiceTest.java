package com.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.domain.room.dto.request.RoomProductCreateRequest;
import com.domain.room.dto.request.RoomStockInitRequest;
import com.domain.room.dto.response.AvailableRoomResponse;
import com.domain.room.dto.response.RoomProductResponse;
import com.domain.room.entity.RoomProduct;
import com.domain.room.entity.RoomStock;
import com.domain.room.entity.RoomType;
import com.domain.room.repository.RoomProductRepository;
import com.domain.room.repository.RoomStockRepository;
import com.global.exception.DomainException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomProductServiceTest {

  @InjectMocks
  private RoomProductService roomProductService;

  @Mock
  private RoomProductRepository roomProductRepository;
  @Mock
  private RoomStockRepository roomStockRepository;

  private RoomProduct createRoom(Long id, boolean isActive) {
    RoomProduct room = RoomProduct.builder()
        .name("디럭스 더블")
        .roomType(RoomType.DELUXE)
        .price(150000)
        .baseCapacity(2)
        .maxCapacity(4)
        .build();
    setField(room, "id", id);
    setField(room, "isActive", isActive);
    return room;
  }

  @Test
  @DisplayName("예약 가능 객실 조회 - 재고 있는 객실만 반환")
  void getAvailableRooms_onlyReturnsRoomsWithStock() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 3);

    RoomProduct room = createRoom(1L, true);
    RoomStock stock1 = RoomStock.builder().roomProduct(room).date(arrDate).stock(3).build();
    RoomStock stock2 = RoomStock.builder().roomProduct(room).date(arrDate.plusDays(1)).stock(2).build();

    given(roomProductRepository.findAll()).willReturn(List.of(room));
    given(roomStockRepository.findByRoomProductIdAndDateBetween(1L, arrDate, depDate.minusDays(1)))
        .willReturn(List.of(stock1, stock2));

    List<AvailableRoomResponse> result = roomProductService.getAvailableRooms(arrDate, depDate, 2);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMinStock()).isEqualTo(2);
  }

  @Test
  @DisplayName("예약 가능 객실 조회 - 재고 0이면 미포함")
  void getAvailableRooms_excludesOutOfStock() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 2);

    RoomProduct room = createRoom(1L, true);
    RoomStock stock = RoomStock.builder().roomProduct(room).date(arrDate).stock(0).build();

    given(roomProductRepository.findAll()).willReturn(List.of(room));
    given(roomStockRepository.findByRoomProductIdAndDateBetween(1L, arrDate, arrDate))
        .willReturn(List.of(stock));

    List<AvailableRoomResponse> result = roomProductService.getAvailableRooms(arrDate, depDate, 2);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("예약 가능 객실 조회 - 인원 초과 객실 미포함")
  void getAvailableRooms_excludesOverCapacityRooms() {
    LocalDate arrDate = LocalDate.of(2026, 7, 1);
    LocalDate depDate = LocalDate.of(2026, 7, 2);

    RoomProduct room = createRoom(1L, true);

    given(roomProductRepository.findAll()).willReturn(List.of(room));

    List<AvailableRoomResponse> result = roomProductService.getAvailableRooms(arrDate, depDate, 5);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("객실 상세 조회 성공")
  void getRoom_success() {
    RoomProduct room = createRoom(1L, true);
    given(roomProductRepository.findById(1L)).willReturn(Optional.of(room));

    RoomProductResponse response = roomProductService.getRoom(1L);

    assertThat(response.getName()).isEqualTo("디럭스 더블");
    assertThat(response.getRoomType()).isEqualTo(RoomType.DELUXE);
  }

  @Test
  @DisplayName("객실 상세 조회 실패 - 존재하지 않는 ID")
  void getRoom_notFound() {
    given(roomProductRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> roomProductService.getRoom(99L))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("객실 상품을 찾을 수 없습니다");
  }

  @Test
  @DisplayName("객실 등록 성공")
  void createRoom_success() {
    RoomProductCreateRequest request = new RoomProductCreateRequest();
    setField(request, "name", "스위트룸");
    setField(request, "roomType", RoomType.SUITE);
    setField(request, "price", 500000);
    setField(request, "baseCapacity", 2);
    setField(request, "maxCapacity", 4);

    RoomProduct room = createRoom(1L, true);
    given(roomProductRepository.save(any())).willReturn(room);

    RoomProductResponse response = roomProductService.createRoom(request);

    assertThat(response).isNotNull();
    verify(roomProductRepository).save(any(RoomProduct.class));
  }

  @Test
  @DisplayName("객실 삭제(비활성화) 성공")
  void deleteRoom_success() {
    RoomProduct room = createRoom(1L, true);
    given(roomProductRepository.findById(1L)).willReturn(Optional.of(room));

    roomProductService.deleteRoom(1L);

    assertThat(room.getIsActive()).isFalse();
  }

  @Test
  @DisplayName("재고 초기화 성공")
  void initStock_success() {
    RoomProduct room = createRoom(1L, true);
    given(roomProductRepository.findById(1L)).willReturn(Optional.of(room));

    RoomStockInitRequest request = new RoomStockInitRequest();
    setField(request, "startDate", LocalDate.of(2026, 7, 1));
    setField(request, "endDate", LocalDate.of(2026, 7, 3));
    setField(request, "stock", 5);

    roomProductService.initStock(1L, request);

    verify(roomStockRepository).deleteByRoomProduct(room);
    verify(roomStockRepository).saveAll(any());
  }

  private void setField(Object obj, String fieldName, Object value) {
    try {
      var field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    } catch (NoSuchFieldException e) {
      Class<?> superClass = obj.getClass().getSuperclass();
      try {
        var field = superClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
