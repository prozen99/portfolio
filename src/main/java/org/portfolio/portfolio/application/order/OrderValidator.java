package org.portfolio.portfolio.application.order;

import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.application.discount.DiscountPolicy;
import org.portfolio.portfolio.application.discount.DiscountPolicyFactory;
import org.portfolio.portfolio.application.exception.InvalidCouponException;
import org.portfolio.portfolio.application.exception.PaymentFailedException;
import org.portfolio.portfolio.application.exception.PriceTamperedException;
import org.portfolio.portfolio.domain.common.CouponType;
import org.portfolio.portfolio.domain.coupon.Coupon;
import org.portfolio.portfolio.domain.user.User;
import org.portfolio.portfolio.domain.usercoupon.UserCoupon;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderValidator {

    private final DiscountPolicyFactory discountPolicyFactory;

    public void validateCouponOwnershipAndUsable(User user, UserCoupon userCoupon) {
        if (userCoupon == null) return;
        if (!userCoupon.getUser().getId().equals(user.getId())) {
            throw new InvalidCouponException("해당 사용자 소유의 쿠폰이 아닙니다.");
        }
        if (userCoupon.isUsed()) {
            throw new InvalidCouponException("이미 사용된 쿠폰입니다.");
        }
    }

    public long calculateDiscount(long originalTotal, Coupon coupon) {
        if (coupon == null) return 0L;
        CouponType type = coupon.getType();
        DiscountPolicy policy = discountPolicyFactory.getPolicy(type);
        long discount = policy.calculateDiscount(originalTotal, coupon);
        if (discount < 0) return 0L;
        return Math.min(discount, originalTotal);
    }

    public long computeFinalAmount(long originalTotal, long discount) {
        long d = Math.max(0L, discount);
        return Math.max(0L, originalTotal - d);
    }

    public void validateClientAmount(long expectedFinalAmount, long clientPayAmount) {
        if (clientPayAmount != expectedFinalAmount) {
            throw new PriceTamperedException("결제 금액 위변조 의심: client=" + clientPayAmount + ", server=" + expectedFinalAmount);
        }
    }

    public void validateApprovedAmount(long approvedAmount, long expectedFinalAmount) {
        if (approvedAmount != expectedFinalAmount) {
            throw new PaymentFailedException("승인 금액 불일치");
        }
    }
}
