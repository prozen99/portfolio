package org.portfolio.portfolio.domain.usercoupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.portfolio.portfolio.domain.common.BaseEntity;
import org.portfolio.portfolio.domain.coupon.Coupon;
import org.portfolio.portfolio.domain.user.User;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_coupon")
public class UserCoupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(nullable = false)
    private boolean used;

    private UserCoupon(User user, Coupon coupon) {
        this.user = user;
        this.coupon = coupon;
        this.used = false;
    }

    public static UserCoupon issueTo(User user, Coupon coupon) {
        UserCoupon uc = new UserCoupon(user, coupon);
        user.addUserCoupon(uc);
        return uc;
    }

    public void use() {
        if (used) throw new IllegalStateException("이미 사용된 쿠폰입니다.");
        this.used = true;
    }

    // 양방향 편의
    public void setUserInternal(User user) {
        this.user = user;
    }
}
