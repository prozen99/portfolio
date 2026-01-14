package org.portfolio.portfolio.domain.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.portfolio.portfolio.domain.common.BaseEntity;
import org.portfolio.portfolio.domain.common.CouponType;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    // 정액(FIXED)일 경우 금액, 정률(RATE)일 경우 퍼센트(0~100)
    @Column(nullable = false)
    private long discountValue;

    private Coupon(String name, CouponType type, long discountValue) {
        this.name = name;
        this.type = type;
        this.discountValue = validate(type, discountValue);
    }

    public static Coupon fixed(String name, long amount) {
        return new Coupon(name, CouponType.FIXED, amount);
    }

    public static Coupon rate(String name, int percent) {
        return new Coupon(name, CouponType.RATE, percent);
    }

    private long validate(CouponType type, long value) {
        if (type == CouponType.FIXED) {
            if (value <= 0) throw new IllegalArgumentException("정액 할인 금액은 1 이상이어야 합니다.");
        } else {
            if (value <= 0 || value > 100) throw new IllegalArgumentException("정률 할인 퍼센트는 1~100 사이여야 합니다.");
        }
        return value;
    }

    public long calculateDiscount(long price) {
        if (type == CouponType.FIXED) {
            return Math.min(price, discountValue);
        }
        return Math.round(price * (discountValue / 100.0));
    }
}
