package org.portfolio.portfolio.application.discount;

import org.portfolio.portfolio.domain.coupon.Coupon;

public interface DiscountPolicy {
    long calculateDiscount(long originalPrice, Coupon coupon);
}
