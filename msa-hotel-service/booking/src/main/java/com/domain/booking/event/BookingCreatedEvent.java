package com.domain.booking.event;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreatedEvent {

    private Long bookingId;
    private String bookingNumber;
    private Long userId;
    private Long roomProductId;
    private LocalDate arrDate;
    private LocalDate depDate;
    private Integer totPrice;
}
