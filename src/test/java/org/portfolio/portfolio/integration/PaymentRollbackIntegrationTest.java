package org.portfolio.portfolio.integration;

import org.junit.jupiter.api.Assertions;
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

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentRollbackIntegrationTest {

    @TestConfiguration
    static class FailingGatewayConfig {
        @Bean
        @Primary
        public PaymentGateway paymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentResult authorize(long amount, String orderId) {
                    return PaymentResult.failure("TEST_FAIL");
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

    @Test
    void payment_failure_should_rollback_order_and_restore_stock() {
        // given: 저장소를 사용해 시드 데이터를 안전하게 저장한다
        String email = "fail+" + System.currentTimeMillis() + "+" + java.util.UUID.randomUUID() + "@test.local";
        User user = userRepository.save(User.create(email, "Fail User"));
        Category category = categoryRepository.save(Category.createRoot("root"));
        Item item = itemRepository.save(Item.create("상품B", 2000L, 5, category));
        em.clear();

        // when
        try {
            orderService.createOrder(user.getId(), item.getId(), 2, null, 4000L);
            Assertions.fail("결제 실패 예외가 발생해야 합니다.");
        } catch (Exception e) {
            // expected
        }

        // then: stock should be restored and no changes persisted
        Item refreshed = itemRepository.findById(item.getId()).orElseThrow();
        Assertions.assertEquals(5, refreshed.getStock(), "롤백 후 재고가 원복되어야 합니다.");
    }
}
