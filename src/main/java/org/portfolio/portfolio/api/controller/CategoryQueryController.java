package org.portfolio.portfolio.api.controller;

import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.api.dto.common.PageResponse;
import org.portfolio.portfolio.api.dto.item.ItemResponse;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.item.ItemRepository;
import org.portfolio.portfolio.domain.item.ItemSearchCondition;
import org.portfolio.portfolio.application.category.CategoryQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryQueryController {

    private final CategoryQueryService categoryQueryService;
    private final ItemRepository itemRepository;

    // 상위 카테고리 포함 하위 카테고리까지의 상품 조회 (시나리오 3 가이드 3번)
    @GetMapping("/{categoryId}/items")
    public PageResponse<ItemResponse> findItemsInHierarchy(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<Long> ids = categoryQueryService.collectDescendantIds(categoryId);
        ItemSearchCondition condition = ItemSearchCondition.builder()
                .categoryIds(ids)
                .build();
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> result = itemRepository.search(condition, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(ItemResponse::from).collect(Collectors.toList()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
