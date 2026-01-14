package org.portfolio.portfolio.domain.item;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.portfolio.portfolio.domain.category.Category;
import org.portfolio.portfolio.domain.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "item")
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private Item(String name, long price, int stock, Category category) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.category = category;
        this.status = ItemStatus.AVAILABLE;
    }

    public static Item create(String name, long price, int stock, Category category) {
        if (price < 0) throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        if (stock < 0) throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        return new Item(name, price, stock, category);
    }

    // 비즈니스 메서드
    public void decreaseStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다.");
        if (this.stock < quantity) throw new IllegalStateException("재고가 부족합니다.");
        this.stock -= quantity;
    }

    public void increaseStock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("증가 수량은 1 이상이어야 합니다.");
        this.stock += quantity;
    }

    public void changeCategory(Category category) {
        this.category = category;
    }

    public void activate() {
        this.status = ItemStatus.AVAILABLE;
    }

    public void deactivate() {
        this.status = ItemStatus.INACTIVE;
    }
}
