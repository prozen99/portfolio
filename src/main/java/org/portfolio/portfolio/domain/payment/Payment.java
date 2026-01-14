package org.portfolio.portfolio.domain.payment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.portfolio.portfolio.domain.common.BaseEntity;
import org.portfolio.portfolio.domain.common.PaymentStatus;
import org.portfolio.portfolio.domain.order.Order;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true, nullable = false)
    private Order order;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private Payment(Order order, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("결제 금액은 1 이상이어야 합니다.");
        this.order = order;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment prepareFor(Order order) {
        return new Payment(order, order.getTotalPrice());
    }

    public static Payment prepareFor(Order order, long amount) {
        return new Payment(order, amount);
    }

    public void markSuccess() {
        if (this.status == PaymentStatus.SUCCESS) return;
        this.status = PaymentStatus.SUCCESS;
        if (this.order != null) this.order.markPaid();
    }

    public void markFailed() {
        if (this.status == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("성공 처리된 결제를 실패로 변경할 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
    }

    // 양방향 편의
    public void setOrderInternal(Order order) {
        this.order = order;
    }
}
