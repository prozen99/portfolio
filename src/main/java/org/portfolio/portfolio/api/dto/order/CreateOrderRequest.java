package org.portfolio.portfolio.api.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long itemId;

    @Min(1)
    private int quantity;

    // 사용하지 않으면 null 허용
    private Long userCouponId;

    @Min(0)
    private long clientPayAmount;
}
