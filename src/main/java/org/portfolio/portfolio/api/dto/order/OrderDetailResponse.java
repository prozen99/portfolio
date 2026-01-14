package org.portfolio.portfolio.api.dto.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.portfolio.portfolio.domain.common.OrderStatus;
import org.portfolio.portfolio.domain.common.PaymentStatus;
import org.portfolio.portfolio.domain.order.Order;
import org.portfolio.portfolio.domain.payment.Payment;

@Getter
@AllArgsConstructor
public class OrderDetailResponse {
    private Long orderId;
    private OrderStatus orderStatus;
    private int quantity;
    private long totalPrice;

    private Long userId;
    private String userName;

    private Long itemId;
    private String itemName;
    private long itemPrice;

    private PaymentStatus paymentStatus; // null일 수 있음
    private Long paymentId; // null일 수 있음
    private Long paymentAmount; // null일 수 있음

    public static OrderDetailResponse from(Order o) {
        Payment p = o.getPayment();
        return new OrderDetailResponse(
                o.getId(),
                o.getStatus(),
                o.getQuantity(),
                o.getTotalPrice(),
                o.getUser() != null ? o.getUser().getId() : null,
                o.getUser() != null ? o.getUser().getName() : null,
                o.getItem() != null ? o.getItem().getId() : null,
                o.getItem() != null ? o.getItem().getName() : null,
                o.getItem() != null ? o.getItem().getPrice() : 0L,
                p != null ? p.getStatus() : null,
                p != null ? p.getId() : null,
                p != null ? p.getAmount() : null
        );
    }
}
