package org.portfolio.portfolio.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.portfolio.portfolio.domain.common.BaseEntity;
import org.portfolio.portfolio.domain.order.Order;
import org.portfolio.portfolio.domain.usercoupon.UserCoupon;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserCoupon> userCoupons = new ArrayList<>();

    private User(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public static User create(String email, String name) {
        return new User(email, name);
    }

    // 비즈니스 메서드
    public void addOrder(Order order) {
        this.orders.add(order);
        order.setUserInternal(this);
    }

    public void addUserCoupon(UserCoupon userCoupon) {
        this.userCoupons.add(userCoupon);
        userCoupon.setUserInternal(this);
    }
}
