package org.portfolio.portfolio.application.order;

import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.application.exception.*;
import org.portfolio.portfolio.application.payment.PaymentGateway;
import org.portfolio.portfolio.application.payment.PaymentResult;
import org.portfolio.portfolio.domain.coupon.Coupon;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.item.ItemRepository;
import org.portfolio.portfolio.domain.order.Order;
import org.portfolio.portfolio.domain.order.OrderRepository;
import org.portfolio.portfolio.domain.payment.Payment;
import org.portfolio.portfolio.domain.user.User;
import org.portfolio.portfolio.domain.user.UserRepository;
import org.portfolio.portfolio.domain.usercoupon.UserCoupon;
import org.portfolio.portfolio.domain.usercoupon.UserCouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final UserCouponRepository userCouponRepository;
    private final OrderRepository orderRepository;

    private final PaymentGateway paymentGateway;
    private final OrderValidator orderValidator;

    @Transactional
    public Long createOrder(Long userId, Long itemId, int quantity, Long userCouponId, long clientPayAmount) {
        if (quantity <= 0) throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");

        // 1) 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다. id=" + userId));

        // 2) 아이템 비관적 락 조회
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new NotFoundException("상품을 찾을 수 없습니다. id=" + itemId));

        // 3) 재고 검증 및 차감(동일 트랜잭션 내에서 롤백 보장)
        if (item.getStock() < quantity) throw new InsufficientStockException("재고가 부족합니다.");
        item.decreaseStock(quantity);

        // 4) 주문 기본 금액 계산
        long originalTotal = item.getPrice() * quantity;

        // 5) 쿠폰 적용 및 할인 계산 (검증 책임 분리)
        long discount = 0L;
        if (userCouponId != null) {
            UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                    .orElseThrow(() -> new NotFoundException("유저 쿠폰을 찾을 수 없습니다. id=" + userCouponId));

            orderValidator.validateCouponOwnershipAndUsable(user, userCoupon);

            Coupon coupon = userCoupon.getCoupon();
            discount = orderValidator.calculateDiscount(originalTotal, coupon);

            // 쿠폰 사용 마킹 (결제 실패시 트랜잭션 롤백으로 자동 복구)
            userCoupon.use();
        }

        long finalAmount = orderValidator.computeFinalAmount(originalTotal, discount);

        // 6) 결제 금액 위변조 검증
        orderValidator.validateClientAmount(finalAmount, clientPayAmount);

        // 7) 주문 생성 및 저장
        Order order = Order.create(user, item, quantity);
        orderRepository.save(order);

        // 무료 결제(최종 금액 0원)인 경우 바로 주문 확정 처리
        if (finalAmount == 0L) {
            order.markPaid();
            return order.getId();
        }

        // 8) 결제 엔티티 생성 및 부착
        Payment payment = Payment.prepareFor(order, finalAmount);
        order.attachPayment(payment);
        orderRepository.save(order); // cascade로 payment 저장

        // 9) 가상 결제 승인 호출
        PaymentResult result = paymentGateway.authorize(finalAmount, String.valueOf(order.getId()));
        if (result.isSuccess()) {
            // 방어적 체크: 승인 금액 일치
            orderValidator.validateApprovedAmount(result.getApprovedAmount(), finalAmount);
            payment.markSuccess();
            return order.getId();
        } else {
            payment.markFailed();
            throw new PaymentFailedException(result.getFailureReason() != null ? result.getFailureReason() : "결제 실패");
        }
    }
}
