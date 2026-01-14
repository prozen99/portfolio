package org.portfolio.portfolio.domain.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemSearchCondition {
    // 단일 카테고리 필터 또는 다중 카테고리 필터(자식 포함 조회 등)에 사용
    private Long categoryId;
    private List<Long> categoryIds;
    private Long minPrice;
    private Long maxPrice;
    private ItemStatus status;
}