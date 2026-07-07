package com.domain.booking.dto.response;

import com.domain.booking.entity.Booking;
import com.domain.booking.entity.BookingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingResponse {

  Long id;
  Long userId;
  Long roomProductId;
  String roomProductName;
  String bookingNumber;
  LocalDateTime bookingDate;
  LocalDate arrDate;
  LocalDate depDate;
  Integer adultCount;
  Integer childCount;
  String guestName;
  String guestPhone;
  String requestMemo;
  Integer totPrice;
  BookingStatus status;
  LocalDateTime cancelDate;
  String cancelReason;

  public static BookingResponse from(Booking booking) {
    return BookingResponse.builder()
        .id(booking.getId())
        .userId(booking.getUserId())
        .roomProductId(booking.getRoomProduct().getId())
        .roomProductName(booking.getRoomProduct().getName())
        .bookingNumber(booking.getBookingNumber())
        .bookingDate(booking.getBookingDate())
        .arrDate(booking.getArrDate())
        .depDate(booking.getDepDate())
        .adultCount(booking.getAdultCount())
        .childCount(booking.getChildCount())
        .guestName(booking.getGuestName())
        .guestPhone(booking.getGuestPhone())
        .requestMemo(booking.getRequestMemo())
        .totPrice(booking.getTotPrice())
        .status(booking.getStatus())
        .cancelDate(booking.getCancelDate())
        .cancelReason(booking.getCancelReason())
        .build();
  }
}
