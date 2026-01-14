package org.portfolio.portfolio.api.controller;

import lombok.RequiredArgsConstructor;
import org.portfolio.portfolio.api.dto.common.PageResponse;
import org.portfolio.portfolio.api.dto.order.CreateOrderRequest;
import org.portfolio.portfolio.api.dto.order.CreateOrderResponse;
import org.portfolio.portfolio.api.dto.order.OrderDetailResponse;
import org.portfolio.portfolio.application.order.OrderService;
import org.portfolio.portfolio.domain.order.Order;
import org.portfolio.portfolio.domain.order.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    // 시나리오 1,2,5: 주문 생성 (비관적 락, 할인 적용, 가상 결제, 금액 위변조 검증)
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse create(@Validated @RequestBody CreateOrderRequest req) {
        Long orderId = orderService.createOrder(
                req.getUserId(), req.getItemId(), req.getQuantity(), req.getUserCouponId(), req.getClientPayAmount()
        );
        // fetch-join으로 상세 조회해 상태 응답
        Order order = orderRepository.findByIdWithUserItemPayment(orderId)
                .orElseThrow();
        String paymentStatus = order.getPayment() != null ? order.getPayment().getStatus().name() : null;
        return new CreateOrderResponse(order.getId(), order.getStatus().name(), paymentStatus);
    }

    // 시나리오 4: 주문 단건 상세 조회 (fetch join)
    @GetMapping("/orders/{orderId}")
    public OrderDetailResponse getOrder(@PathVariable Long orderId) {
        Order order = orderRepository.findByIdWithUserItemPayment(orderId)
                .orElseThrow();
        return OrderDetailResponse.from(order);
    }

    // 시나리오 4: 사용자 주문 목록 조회 (fetch join) — 안전한 DB 페이징
    @GetMapping("/users/{userId}/orders")
    public PageResponse<OrderDetailResponse> getUserOrders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderRepository.findPageByUserIdWithItemPayment(userId, pageable);
        List<OrderDetailResponse> content = orders.getContent().stream()
                .map(OrderDetailResponse::from)
                .collect(Collectors.toList());
        return new PageResponse<>(content, orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages());
    }
}
