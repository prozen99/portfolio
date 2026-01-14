package org.portfolio.portfolio.domain.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.portfolio.portfolio.domain.common.BaseEntity;
import org.portfolio.portfolio.domain.common.OrderStatus;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.payment.Payment;
import org.portfolio.portfolio.domain.user.User;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    private Order(User user, Item item, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        this.user = user;
        this.item = item;
        this.quantity = quantity;
        this.totalPrice = item.getPrice() * quantity;
        this.status = OrderStatus.CREATED;
    }

    public static Order create(User user, Item item, int quantity) {
        // 주문 생성 시 재고 차감은 서비스 레이어에서 트랜잭션 안에서 수행하도록 함
        Order order = new Order(user, item, quantity);
        user.addOrder(order);
        return order;
    }

    // 비즈니스 메서드
    public void markPaid() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("취소된 주문은 결제할 수 없습니다.");
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        if (this.status == OrderStatus.PAID) {
            throw new IllegalStateException("결제된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void attachPayment(Payment payment) {
        this.payment = payment;
        payment.setOrderInternal(this);
    }

    // 양방향 설정 내부 메서드
    public void setUserInternal(User user) {
        this.user = user;
    }
}
