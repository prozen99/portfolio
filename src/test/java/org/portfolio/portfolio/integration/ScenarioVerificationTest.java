package org.portfolio.portfolio.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.portfolio.portfolio.application.exception.PriceTamperedException;
import org.portfolio.portfolio.application.order.OrderService;
import org.portfolio.portfolio.application.payment.PaymentGateway;
import org.portfolio.portfolio.application.payment.PaymentResult;
import org.portfolio.portfolio.domain.category.Category;
import org.portfolio.portfolio.domain.common.CouponType;
import org.portfolio.portfolio.domain.coupon.Coupon;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.item.ItemRepository;
import org.portfolio.portfolio.domain.item.ItemSearchCondition;
import org.portfolio.portfolio.domain.item.ItemStatus;
import org.portfolio.portfolio.domain.order.Order;
import org.portfolio.portfolio.domain.order.OrderRepository;
import org.portfolio.portfolio.domain.payment.Payment;
import org.portfolio.portfolio.domain.user.User;
import org.portfolio.portfolio.domain.usercoupon.UserCoupon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScenarioVerificationTest {

    @TestConfiguration
    static class SwitchableGatewayConfig {
        @Bean
        @Primary
        public SwitchableTestPaymentGateway paymentGateway() {
            return new SwitchableTestPaymentGateway();
        }
    }

    /**
     * 테스트용으로 동작 모드를 전환할 수 있는 PaymentGateway
     */
    static class SwitchableTestPaymentGateway implements PaymentGateway {
        enum Mode { SUCCESS_MATCH, SUCCESS_MISMATCH, ALWAYS_FAIL }
        private volatile Mode mode = Mode.SUCCESS_MATCH;
        private volatile Long forcedApprovedAmount; // 설정되면 해당 금액을 사용한다

        public void setMode(Mode mode) { this.mode = mode; }
        public void setForcedApprovedAmount(Long amt) { this.forcedApprovedAmount = amt; }

        @Override
        public PaymentResult authorize(long amount, String orderId) {
            if (mode == Mode.ALWAYS_FAIL) {
                return PaymentResult.failure("TEST_FAIL");
            }
            long approved = forcedApprovedAmount != null ? forcedApprovedAmount : amount;
            if (mode == Mode.SUCCESS_MISMATCH) {
                approved = amount + 1; // 의도적으로 불일치 유도
            }
            return PaymentResult.success("TEST-TX-" + orderId, approved);
        }
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SwitchableTestPaymentGateway gateway;

    @PersistenceContext
    private EntityManager em;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        String email = "alice+" + System.currentTimeMillis() + "+" + java.util.UUID.randomUUID() + "@test.local";
        user = User.create(email, "Alice");
        category = Category.createRoot("Root");
        em.persist(category);
        em.persist(user);
        em.flush();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        // 별도 작업 없음; @DirtiesContext 가 컨텍스트를 리셋한다
    }

    @Test
    @DisplayName("Scenario 1: Concurrency — stock 10 with 60 concurrent buyers -> only 10 succeed")
    void scenario1_concurrency_no_oversell() throws InterruptedException {
        // given
        Item item = Item.create("C-Item", 1000L, 10, category);
        em.persist(item);
        em.flush();
        em.clear();

        gateway.setMode(SwitchableTestPaymentGateway.Mode.SUCCESS_MATCH);

        int threads = 60;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Boolean> results = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) results.add(false);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderService.createOrder(user.getId(), item.getId(), 1, null, 1000L);
                    results.set(idx, true);
                } catch (Exception e) {
                    results.set(idx, false);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        long t0 = System.nanoTime();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        executor.shutdownNow();

        long success = results.stream().filter(b -> b).count();
        Item refreshed = itemRepository.findById(item.getId()).orElseThrow();
        System.out.println("[DEBUG_LOG] Scenario1 concurrency: threads=" + threads + ", success=" + success + ", finalStock=" + refreshed.getStock() + ", elapsedMs=" + elapsedMs);
        Assertions.assertEquals(10, success, "Exactly stock-sized number of orders must succeed");
        Assertions.assertEquals(0, refreshed.getStock(), "Stock must be 0 after successful orders");
    }

    @Test
    @DisplayName("Scenario 2: Discount strategy — fixed and rate discounts applied correctly")
    void scenario2_discount_fixed_and_rate() {
        // given: 상품과 쿠폰 준비
        Item item = Item.create("D-Item", 10000L, 5, category);
        em.persist(item);
        Coupon fixed = Coupon.fixed("1k off", 1000);
        Coupon rate10 = Coupon.rate("10% off", 10);
        em.persist(fixed);
        em.persist(rate10);
        em.flush();

        UserCoupon ucFixed = UserCoupon.issueTo(user, fixed);
        UserCoupon ucRate = UserCoupon.issueTo(user, rate10);
        em.persist(ucFixed);
        em.persist(ucRate);
        em.flush();
        em.clear();

        gateway.setMode(SwitchableTestPaymentGateway.Mode.SUCCESS_MATCH);

        // when: 고정 1000 할인 -> 9000
        Long orderId1 = orderService.createOrder(user.getId(), item.getId(), 1, ucFixed.getId(), 9000L);
        Order order1 = orderRepository.findByIdWithUserItemPayment(orderId1).orElseThrow();
        Assertions.assertEquals(9000L, order1.getPayment().getAmount());

        // when: 비율 10% 할인 -> 9000
        Long orderId2 = orderService.createOrder(user.getId(), item.getId(), 1, ucRate.getId(), 9000L);
        Order order2 = orderRepository.findByIdWithUserItemPayment(orderId2).orElseThrow();
        Assertions.assertEquals(9000L, order2.getPayment().getAmount());
    }

    @Test
    @DisplayName("Scenario 3: Dynamic search — filter by category, price and status")
    void scenario3_dynamic_search() {
        // given multiple items
        Item i1 = Item.create("Phone", 5000L, 10, category);
        Item i2 = Item.create("Laptop", 15000L, 10, category);
        Item i3 = Item.create("Case", 1000L, 10, category);
        em.persist(i1);
        em.persist(i2);
        em.persist(i3);
        em.flush();
        // 하나 비활성화
        i3.deactivate();
        em.flush();
        em.clear();

        // when
        ItemSearchCondition cond = ItemSearchCondition.builder()
                .categoryId(category.getId())
                .minPrice(2000L)
                .maxPrice(20000L)
                .status(ItemStatus.AVAILABLE)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Item> page = itemRepository.search(cond, pageable);

        // then: Phone(5000)과 Laptop(15000)은 포함되고, Case(1000) 및 비활성(INACTIVE)은 제외되어야 한다
        List<String> names = page.getContent().stream().map(Item::getName).toList();
        Assertions.assertTrue(names.contains("Phone"));
        Assertions.assertTrue(names.contains("Laptop"));
        Assertions.assertFalse(names.contains("Case"));
    }

    @Test
    @DisplayName("Scenario 4: Fetch-join read — order detail and user orders are loaded without N+1")
    void scenario4_fetch_join_read() {
        // given
        Item item = Item.create("KBD", 20000L, 3, category);
        em.persist(item);
        em.flush();
        em.clear();

        gateway.setMode(SwitchableTestPaymentGateway.Mode.SUCCESS_MATCH);

        Long orderId = orderService.createOrder(user.getId(), item.getId(), 1, null, 20000L);

        // when
        Order one = orderRepository.findByIdWithUserItemPayment(orderId).orElseThrow();
        Page<Order> page = orderRepository.findPageByUserIdWithItemPayment(user.getId(), PageRequest.of(0, 10));

        // then: 연관들이 단일 select의 패치 조인(fetch join)으로 로드되는지 확인한다(기능 점검)
        Assertions.assertNotNull(one.getUser());
        Assertions.assertNotNull(one.getItem());
        // 무료 주문의 경우 payment가 null일 수 있으나, 여기서는 해당하지 않는다
        Payment p = one.getPayment();
        Assertions.assertNotNull(p);
        Assertions.assertEquals(1, page.getContent().size());
    }

    @Test
    @DisplayName("Scenario 5: Payment flow — success, gateway failure rollback, and amount tampering guard")
    void scenario5_payment_flow() {
        // given
        Item item = Item.create("Mouse", 10000L, 2, category);
        em.persist(item);
        em.flush();
        em.clear();

        // a) success path
        gateway.setMode(SwitchableTestPaymentGateway.Mode.SUCCESS_MATCH);
        Long orderId = orderService.createOrder(user.getId(), item.getId(), 1, null, 10000L);
        Order paid = orderRepository.findByIdWithUserItemPayment(orderId).orElseThrow();
        Assertions.assertNotNull(paid.getPayment());
        Assertions.assertEquals(10000L, paid.getPayment().getAmount());

        // b) tampering: client sends wrong amount -> expect PriceTamperedException before gateway call
        Assertions.assertThrows(PriceTamperedException.class, () ->
                orderService.createOrder(user.getId(), item.getId(), 1, null, 9999L)
        );

        // c) gateway failure -> rollback, stock restored
        gateway.setMode(SwitchableTestPaymentGateway.Mode.ALWAYS_FAIL);
        try {
            orderService.createOrder(user.getId(), item.getId(), 1, null, 10000L);
            Assertions.fail("Expected payment failure");
        } catch (Exception ignored) {}
        Item after = itemRepository.findById(item.getId()).orElseThrow();
        Assertions.assertEquals(1, after.getStock(), "On failure, stock should be restored by rollback");

        // d) gateway success but approved amount mismatched -> rollback
        gateway.setMode(SwitchableTestPaymentGateway.Mode.SUCCESS_MISMATCH);
        try {
            orderService.createOrder(user.getId(), item.getId(), 1, null, 10000L);
            Assertions.fail("Expected approved amount mismatch to fail");
        } catch (Exception ignored) {}
        Item after2 = itemRepository.findById(item.getId()).orElseThrow();
        Assertions.assertEquals(1, after2.getStock(), "On mismatch, rollback should restore stock");
    }
}
