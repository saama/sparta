package com.domain.room.controller;

import com.domain.room.dto.request.RoomProductCreateRequest;
import com.domain.room.dto.request.RoomStockInitRequest;
import com.domain.room.dto.response.AvailableRoomResponse;
import com.domain.room.dto.response.RoomProductResponse;
import com.domain.room.service.RoomProductService;
import com.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoomProductController {

  private final RoomProductService roomProductService;

  @GetMapping("/api/rooms")
  public ApiResponse<List<AvailableRoomResponse>> getAvailableRooms(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depDate,
      @RequestParam(defaultValue = "1") int guestCount) {
    return ApiResponse.ok(roomProductService.getAvailableRooms(arrDate, depDate, guestCount));
  }

  @GetMapping("/api/rooms/{id}")
  public ApiResponse<RoomProductResponse> getRoom(@PathVariable Long id) {
    return ApiResponse.ok(roomProductService.getRoom(id));
  }

  @PostMapping("/api/admin/rooms")
  public ApiResponse<RoomProductResponse> createRoom(
      @Valid @RequestBody RoomProductCreateRequest request) {
    return ApiResponse.ok(roomProductService.createRoom(request));
  }

  @PutMapping("/api/admin/rooms/{id}")
  public ApiResponse<RoomProductResponse> updateRoom(@PathVariable Long id,
      @Valid @RequestBody RoomProductCreateRequest request) {
    return ApiResponse.ok(roomProductService.updateRoom(id, request));
  }

  @DeleteMapping("/api/admin/rooms/{id}")
  public ApiResponse<Void> deleteRoom(@PathVariable Long id) {
    roomProductService.deleteRoom(id);
    return ApiResponse.ok();
  }

  @PostMapping("/api/admin/rooms/{id}/stock")
  public ApiResponse<Void> initStock(@PathVariable Long id,
      @Valid @RequestBody RoomStockInitRequest request) {
    roomProductService.initStock(id, request);
    return ApiResponse.ok();
  }
}
