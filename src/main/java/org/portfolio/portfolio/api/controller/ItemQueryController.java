package org.portfolio.portfolio.api.controller;

import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.api.dto.common.PageResponse;
import org.portfolio.portfolio.api.dto.item.ItemResponse;
import org.portfolio.portfolio.api.dto.item.ItemSearchRequest;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.item.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/items")
public class ItemQueryController {

    private final ItemRepository itemRepository;

    // 시나리오 3: QueryDSL 동적 검색 API
    @GetMapping
    public PageResponse<ItemResponse> search(@ModelAttribute ItemSearchRequest request) {
        Page<Item> page = itemRepository.search(request.toCondition(), request.toPageable());
        return new PageResponse<>(
                page.getContent().stream().map(ItemResponse::from).collect(Collectors.toList()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
