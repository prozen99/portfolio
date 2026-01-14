package org.portfolio.portfolio.application.discount;

import org.portfolio.portfolio.domain.coupon.Coupon;
import org.springframework.stereotype.Component;

@Component
public class RateDiscountPolicy implements DiscountPolicy {
    @Override
    public long calculateDiscount(long originalPrice, Coupon coupon) {
        if (coupon == null) return 0L;
        long discount = coupon.calculateDiscount(originalPrice);
        if (discount < 0) return 0L;
        return Math.min(discount, originalPrice);
    }
}
