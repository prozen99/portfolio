package org.portfolio.portfolio.api.dto.item;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.item.ItemStatus;

@Getter
@AllArgsConstructor
public class ItemResponse {
    private Long id;
    private String name;
    private long price;
    private int stock;
    private ItemStatus status;
    private Long categoryId;
    private String categoryName;

    public static ItemResponse from(Item item) {
        Long categoryId = item.getCategory() != null ? item.getCategory().getId() : null;
        String categoryName = item.getCategory() != null ? item.getCategory().getName() : null;
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getPrice(),
                item.getStock(),
                item.getStatus(),
                categoryId,
                categoryName
        );
    }
}
