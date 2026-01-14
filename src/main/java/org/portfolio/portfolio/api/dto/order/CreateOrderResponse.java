package org.portfolio.portfolio.api.dto.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateOrderResponse {
    private Long orderId;
    private String orderStatus;
    private String paymentStatus; // 무료 주문(결제 없음)이거나 결제가 아직 연결되기 전에는 null일 수 있다
}
