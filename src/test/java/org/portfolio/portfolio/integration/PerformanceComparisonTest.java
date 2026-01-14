package org.portfolio.portfolio.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.portfolio.portfolio.application.discount.DiscountPolicyFactory;
import org.portfolio.portfolio.application.order.OrderService;
import org.portfolio.portfolio.application.payment.PaymentGateway;
import org.portfolio.portfolio.application.payment.PaymentResult;
import org.portfolio.portfolio.domain.category.Category;
import org.portfolio.portfolio.domain.coupon.Coupon;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.item.ItemRepository;
import org.portfolio.portfolio.domain.order.Order;
import org.portfolio.portfolio.domain.order.OrderRepository;
import org.portfolio.portfolio.domain.payment.Payment;
import org.portfolio.portfolio.domain.user.User;
import org.portfolio.portfolio.domain.user.UserRepository;
import org.portfolio.portfolio.domain.usercoupon.UserCoupon;
import org.portfolio.portfolio.domain.usercoupon.UserCouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * PerformanceComparisonTest
 * 
 * 시나리오 1~5에 대해 BEFORE와 AFTER를 비교한다:
 * - 시나리오 1: 동시성 환경에서의 데이터 일관성 (no lock vs 비관적 락)
 * - 시나리오 3 & 4: N+1 vs 패치 조인(fetch join) + batch size (쿼리 수와 시간)
 * - 시나리오 2 & 5: 로직 분리 영향 (단일 서비스 vs OrderService + OrderValidator)
 *
 * 마지막에 통합된 [성능 벤치마크 보고서]를 출력한다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PerformanceComparisonTest {

    @TestConfiguration
    static class TestBeansConfig {
        @Bean
        @Primary
        public PaymentGateway testPaymentGateway() {
            // 성능 수치를 안정적으로 얻기 위한 결정적 성공 게이트웨이
            return (amount, orderId) -> PaymentResult.success("TEST-TX-" + orderId, amount);
        }

        // BEFORE: 비관적 락 없이, OrderValidator 분리 없이 동작하는 단순 서비스
        @Bean
        public NaiveOrderServiceWithoutLock naiveOrderServiceWithoutLock(
                UserRepository userRepository,
                ItemRepository itemRepository,
                UserCouponRepository userCouponRepository,
                OrderRepository orderRepository,
                PaymentGateway paymentGateway,
                DiscountPolicyFactory discountPolicyFactory
        ) {
            return new NaiveOrderServiceWithoutLock(userRepository, itemRepository, userCouponRepository, orderRepository, paymentGateway, discountPolicyFactory);
        }
    }

    static class NaiveOrderServiceWithoutLock {
        private final UserRepository userRepository;
        private final ItemRepository itemRepository;
        private final UserCouponRepository userCouponRepository;
        private final OrderRepository orderRepository;
        private final PaymentGateway paymentGateway;
        private final DiscountPolicyFactory discountPolicyFactory; // 계산 일관성을 위해 기존 정책을 그대로 재사용한다

        public NaiveOrderServiceWithoutLock(UserRepository userRepository,
                                            ItemRepository itemRepository,
                                            UserCouponRepository userCouponRepository,
                                            OrderRepository orderRepository,
                                            PaymentGateway paymentGateway,
                                            DiscountPolicyFactory discountPolicyFactory) {
            this.userRepository = userRepository;
            this.itemRepository = itemRepository;
            this.userCouponRepository = userCouponRepository;
            this.orderRepository = orderRepository;
            this.paymentGateway = paymentGateway;
            this.discountPolicyFactory = discountPolicyFactory;
        }

        @Transactional
        public Long createOrder(Long userId, Long itemId, int quantity, Long userCouponId, long clientPayAmount) {
            // 의도적으로 findById 사용 (락 없음)
            User user = userRepository.findById(userId).orElseThrow();
            Item item = itemRepository.findById(itemId).orElseThrow();

            if (item.getStock() < quantity) throw new IllegalStateException("재고가 부족합니다.");
            // 경합에 취약한 재고 차감 (락 없음)
            item.decreaseStock(quantity);

            long originalTotal = item.getPrice() * quantity;

            long discount = 0L;
            if (userCouponId != null) {
                UserCoupon userCoupon = userCouponRepository.findById(userCouponId).orElseThrow();
                if (!userCoupon.getUser().getId().equals(user.getId()) || userCoupon.isUsed()) {
                    throw new IllegalStateException("쿠폰 사용 불가");
                }
                Coupon coupon = userCoupon.getCoupon();
                // 정책을 통해 바로 할인 금액을 계산한다
                discount = discountPolicyFactory.getPolicy(coupon.getType()).calculateDiscount(originalTotal, coupon);
                if (discount < 0) discount = 0;
                if (discount > originalTotal) discount = originalTotal;
                userCoupon.use();
            }
            long finalAmount = Math.max(0, originalTotal - Math.max(0, discount));
            if (clientPayAmount != finalAmount) throw new IllegalArgumentException("금액 불일치");

            Order order = Order.create(user, item, quantity);
            orderRepository.save(order);

            if (finalAmount == 0L) {
                order.markPaid();
                return order.getId();
            }

            Payment payment = Payment.prepareFor(order, finalAmount);
            order.attachPayment(payment);
            orderRepository.save(order);

            PaymentResult result = paymentGateway.authorize(finalAmount, String.valueOf(order.getId()));
            if (result.isSuccess()) {
                // 승인 금액 엄격 검증 없음 (BEFORE 단계)
                payment.markSuccess();
                return order.getId();
            } else {
                payment.markFailed();
                throw new IllegalStateException("결제 실패");
            }
        }
    }

    @Autowired
    private OrderService orderService; // AFTER
    @Autowired
    private NaiveOrderServiceWithoutLock naiveService; // BEFORE

    @Autowired private UserRepository userRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserCouponRepository userCouponRepository;

    @PersistenceContext
    private EntityManager em;

    @Autowired private org.portfolio.portfolio.domain.category.CategoryRepository categoryRepository;
    @Autowired private TransactionTemplate tx;

    // 영속 작업을 트랜잭션으로 안전하게 수행하기 위한 시드 헬퍼들
    private User seedUser(String email, String name) {
        return userRepository.save(User.create(email, name));
    }
    private Category seedCategory(String name) {
        return categoryRepository.save(Category.createRoot(name));
    }
    private Item seedItem(String name, long price, int stock, Category category) {
        return itemRepository.save(Item.create(name, price, stock, category));
    }
    private Coupon seedFixedCoupon(String name, long amount) {
        return tx.execute(status -> {
            Coupon c = Coupon.fixed(name, amount);
            em.persist(c);
            em.flush();
            return c;
        });
    }
    private UserCoupon issueCouponToUser(Long userId, Long couponId) {
        return tx.execute(status -> {
            User u = userRepository.findById(userId).orElseThrow();
            Coupon c = em.find(Coupon.class, couponId);
            UserCoupon uc = UserCoupon.issueTo(u, c);
            em.persist(uc);
            em.flush();
            return uc;
        });
    }

    private record RunResult(long elapsedMs, long successCount, int finalStock, long queryCount, long preparedCount) {}

    @Test
    @DisplayName("Performance comparison across scenarios 1~5 (Before vs After)")
    void performance_comparison_report() throws Exception {
        // 공통 데이터 시드(트랜잭션 헬퍼 사용)
        String benchEmail = "bench+" + System.currentTimeMillis() + "+" + java.util.UUID.randomUUID() + "@test.local";
        User user = seedUser(benchEmail, "Bench");
        Category category = seedCategory("BenchCat");

        // 시나리오 1 — 동시성: BEFORE (락 없음)
        Item itemBefore = seedItem("S1-Item", 1000L, 10, category);
        em.clear();

        RunResult s1Before = runConcurrencyScenario(user.getId(), itemBefore.getId(), true);

        // Scenario 1 — Concurrency: AFTER (pessimistic lock)
        Item itemAfter = seedItem("S1-Item-L", 1000L, 10, category);
        em.clear();

        RunResult s1After = runConcurrencyScenario(user.getId(), itemAfter.getId(), false);

        // Scenario 2 & 5 — Monolithic vs Validator/Strategy (throughput over N orders)
        // Prepare coupons
        Coupon fixed = seedFixedCoupon("Fixed1000", 1000);
        UserCoupon uc = issueCouponToUser(user.getId(), fixed.getId());
        Item itemPolicy = seedItem("S2-Item", 10000L, 300, category);
        em.clear();

        RunResult s2Before = runBulkOrdersMonolith(user.getId(), itemPolicy.getId(), uc.getId(), 100);
        // Re-issue a coupon for AFTER run
        UserCoupon uc2 = issueCouponToUser(user.getId(), fixed.getId());
        em.clear();
        RunResult s2After = runBulkOrdersAfter(user.getId(), itemPolicy.getId(), uc2.getId(), 100);

        // Scenario 3 & 4 — Query counts: BEFORE (N+1) vs AFTER (fetch-join + paging)
        QueryStats qBefore = runQueryOrdersBefore(user.getId(), category);
        QueryStats qAfter = runQueryOrdersAfter(user.getId());

        // 통합 보고서를 출력한다
        printReport(s1Before, s1After, s2Before, s2After, qBefore, qAfter);
    }

    private RunResult runConcurrencyScenario(Long userId, Long itemId, boolean before) throws InterruptedException {
        int threads = 30; // 60에서 축소: 커넥션 고갈 방지 및 안정성 향상
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
                    if (before) {
                        naiveService.createOrder(userId, itemId, 1, null, 1000L);
                    } else {
                        orderService.createOrder(userId, itemId, 1, null, 1000L);
                    }
                    results.set(idx, true);
                } catch (Exception e) {
                    results.set(idx, false);
                    System.out.println("[DEBUG_LOG] Scenario1 thread error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
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
        int finalStock = itemRepository.findById(itemId).orElseThrow().getStock();
        return new RunResult(elapsedMs, success, finalStock, 0, 0);
    }

    private RunResult runBulkOrdersMonolith(Long userId, Long itemId, Long userCouponId, int n) {
        long t0 = System.nanoTime();
        long success = 0;
        for (int i = 0; i < n; i++) {
            try {
                naiveService.createOrder(userId, itemId, 1, (i == 0 ? userCouponId : null), (i == 0 ? 9000L : 10000L));
                success++;
            } catch (Exception e) {
                System.out.println("[DEBUG_LOG] S2 BEFORE order " + i + " failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        int finalStock = itemRepository.findById(itemId).orElseThrow().getStock();
        return new RunResult(elapsedMs, success, finalStock, 0, 0);
    }

    private RunResult runBulkOrdersAfter(Long userId, Long itemId, Long userCouponId, int n) {
        long t0 = System.nanoTime();
        long success = 0;
        for (int i = 0; i < n; i++) {
            try {
                orderService.createOrder(userId, itemId, 1, (i == 0 ? userCouponId : null), (i == 0 ? 9000L : 10000L));
                success++;
            } catch (Exception ignored) {}
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        int finalStock = itemRepository.findById(itemId).orElseThrow().getStock();
        return new RunResult(elapsedMs, success, finalStock, 0, 0);
    }

    private Statistics enableAndClearStats() {
        SessionFactory sf = em.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics st = sf.getStatistics();
        st.setStatisticsEnabled(true);
        st.clear();
        return st;
    }

    private record QueryStats(long timeMs, long queryExecutionCount, long preparedStatementCount, int rows) {}

    private QueryStats runQueryOrdersBefore(Long userId, Category category) {
        // Seed 100 orders for the user with associated item/payment
        Item seedItem = seedItem("Q-Item", 2000L, 200, category);
        em.clear();
        for (int i = 0; i < 100; i++) {
            orderService.createOrder(userId, seedItem.getId(), 1, null, 2000L);
        }
        em.clear();

        return tx.execute(status -> {
            Statistics st = enableAndClearStats();
            long t0 = System.nanoTime();
            // BEFORE: N+1에 취약 — 단순 로딩 후 지연 연관을 접근한다
            List<Order> all = orderRepository.findAll(); // loads orders lazily
            int touched = 0;
            for (Order o : all) {
                if (o.getUser() != null) { o.getUser().getName(); }
                if (o.getItem() != null) { o.getItem().getName(); }
                Payment p = o.getPayment();
                if (p != null) { p.getAmount(); }
                touched++;
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            long qExec = st.getQueryExecutionCount();
            long prepared = st.getPrepareStatementCount();
            return new QueryStats(elapsedMs, qExec, prepared, touched);
        });
    }

    private QueryStats runQueryOrdersAfter(Long userId) {
        Statistics st = enableAndClearStats();
        long t0 = System.nanoTime();
        Pageable pageable = PageRequest.of(0, 100);
        Page<Order> page = orderRepository.findPageByUserIdWithItemPayment(userId, pageable);
        // Access a few fields to ensure data materialized
        for (Order o : page.getContent()) {
            if (o.getUser() != null) { o.getUser().getName(); }
            if (o.getItem() != null) { o.getItem().getName(); }
            Payment p = o.getPayment();
            if (p != null) { p.getAmount(); }
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        long qExec = st.getQueryExecutionCount();
        long prepared = st.getPrepareStatementCount();
        return new QueryStats(elapsedMs, qExec, prepared, page.getNumberOfElements());
    }

    private void printReport(RunResult s1Before, RunResult s1After,
                             RunResult s2Before, RunResult s2After,
                             QueryStats qBefore, QueryStats qAfter) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("\n[Performance Benchmark Report]\n");
        sb.append("==============================================================\n");
        sb.append("Scenario 1 — Concurrency & Consistency\n");
        sb.append(String.format("Before: time=%dms, success=%d, finalStock=%d (no lock)\n",
                s1Before.elapsedMs(), s1Before.successCount(), s1Before.finalStock()));
        sb.append(String.format("After : time=%dms, success=%d, finalStock=%d (pessimistic lock)\n",
                s1After.elapsedMs(), s1After.successCount(), s1After.finalStock()));
        sb.append("\n");

        sb.append("Scenario 3 & 4 — Query Optimization (N+1 vs Fetch-Join + Batch)\n");
        sb.append(String.format("Before: time=%dms, queryExec=%d, prepared=%d\n",
                qBefore.timeMs(), qBefore.queryExecutionCount(), qBefore.preparedStatementCount()));
        sb.append(String.format("After : time=%dms, queryExec=%d, prepared=%d\n",
                qAfter.timeMs(), qAfter.queryExecutionCount(), qAfter.preparedStatementCount()));
        sb.append("\n");

        sb.append("Scenario 2 & 5 — Service Layering (Monolith vs Validator/Strategy)\n");
        sb.append(String.format("Before: time=%dms, success=%d\n", s2Before.elapsedMs(), s2Before.successCount()));
        sb.append(String.format("After : time=%dms, success=%d\n", s2After.elapsedMs(), s2After.successCount()));
        sb.append("==============================================================\n");

        // Pretty table style output
        sb.append("\n| Scenario | Before Time (ms) | After Time (ms) | Before Success | After Success | Notes |\n");
        sb.append("|---------|------------------:|----------------:|---------------:|--------------:|-------|\n");
        sb.append(String.format("| S1 Concurrency | %6d | %6d | %3d | %3d | stock(Before)=%d, stock(After)=%d |\n",
                s1Before.elapsedMs(), s1After.elapsedMs(), s1Before.successCount(), s1After.successCount(), s1Before.finalStock(), s1After.finalStock()));
        sb.append(String.format("| S3&4 Queries   | %6d | %6d |  -  |  -  | q(Before)=%d, q(After)=%d |\n",
                qBefore.timeMs(), qAfter.timeMs(), qBefore.queryExecutionCount(), qAfter.queryExecutionCount()));
        sb.append(String.format("| S2&5 Service   | %6d | %6d | %3d | %3d | validator+strategy |\n",
                s2Before.elapsedMs(), s2After.elapsedMs(), s2Before.successCount(), s2After.successCount()));

        System.out.println(sb.toString());
    }
}
