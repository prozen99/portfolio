package org.portfolio.portfolio.api.dto.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.portfolio.portfolio.domain.item.ItemSearchCondition;
import org.portfolio.portfolio.domain.item.ItemStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Getter
@NoArgsConstructor
public class ItemSearchRequest {
    private Long categoryId;
    private Long minPrice;
    private Long maxPrice;
    private ItemStatus status;

    // 페이징 파라미터
    private Integer page = 0;
    private Integer size = 20;
    private String sort = "createdAt,desc"; // 향후 확장: BaseEntity를 통해 Item에 createdAt 추가 시 정렬에 사용

    public ItemSearchCondition toCondition() {
        return ItemSearchCondition.builder()
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .status(status)
                .build();
    }

    public Pageable toPageable() {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page == null ? 0 : page, size == null ? 20 : size);
        }
        String[] parts = sort.split(",");
        String prop = parts[0];
        Sort.Direction dir = (parts.length > 1 && parts[1].equalsIgnoreCase("asc")) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page == null ? 0 : page, size == null ? 20 : size, Sort.by(dir, prop));
    }
}
