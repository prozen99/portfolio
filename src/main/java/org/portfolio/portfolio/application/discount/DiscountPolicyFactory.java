package org.portfolio.portfolio.application.discount;

import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.domain.common.CouponType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscountPolicyFactory {

    private final FixDiscountPolicy fixDiscountPolicy;
    private final RateDiscountPolicy rateDiscountPolicy;

    public DiscountPolicy getPolicy(CouponType type) {
        if (type == null) throw new IllegalArgumentException("CouponType must not be null");
        return switch (type) {
            case FIXED -> fixDiscountPolicy;
            case RATE -> rateDiscountPolicy;
        };
    }
}
