package org.portfolio.portfolio.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.portfolio.portfolio.application.order.OrderService;
import org.portfolio.portfolio.application.payment.PaymentGateway;
import org.portfolio.portfolio.application.payment.PaymentResult;
import org.portfolio.portfolio.domain.category.Category;
import org.portfolio.portfolio.domain.category.CategoryRepository;
import org.portfolio.portfolio.domain.item.Item;
import org.portfolio.portfolio.domain.item.ItemRepository;
import org.portfolio.portfolio.domain.user.User;
import org.portfolio.portfolio.domain.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
class ConcurrencyIntegrationTest {

    @TestConfiguration
    static class SuccessGatewayConfig {
        @Bean
        @Primary
        public PaymentGateway paymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentResult authorize(long amount, String orderId) {
                    return PaymentResult.success("TEST-TX-" + orderId, amount);
                }
            };
        }
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    @PersistenceContext
    private EntityManager em;

    private Long userId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        String email = "user+" + System.currentTimeMillis() + "+" + java.util.UUID.randomUUID() + "@test.local";
        User user = userRepository.save(User.create(email, "User1"));
        Category category = categoryRepository.save(Category.createRoot("root"));
        Item item = itemRepository.save(Item.create("상품A", 1000L, 10, category));
        this.userId = user.getId();
        this.itemId = item.getId();
        em.clear();
    }

    @AfterEach
    void cleanUp() {
        // 테스트 간 DB 정리가 필요하면 여기서 수행한다(선택)
    }

    @Test
    void stock10_concurrent100_only10_success_and_stock0() throws InterruptedException {
        // given

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<Boolean> results = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            results.add(false);
        }

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderService.createOrder(userId, itemId, 1, null, 1000L);
                    results.set(idx, true);
                } catch (Exception e) {
                    results.set(idx, false);
                } finally {
                    done.countDown();
                }
            });
        }

        // 시작 동기화
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        long success = results.stream().filter(b -> b).count();

        // then
        Item refreshed = itemRepository.findById(itemId).orElseThrow();
        Assertions.assertEquals(10, success, "정확히 10건만 성공해야 합니다.");
        Assertions.assertEquals(0, refreshed.getStock(), "남은 재고가 0이어야 합니다.");
    }
}
