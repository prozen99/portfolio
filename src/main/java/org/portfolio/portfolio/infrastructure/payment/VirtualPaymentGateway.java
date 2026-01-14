package org.portfolio.portfolio.infrastructure.payment;

import org.portfolio.portfolio.application.payment.PaymentGateway;
import org.portfolio.portfolio.application.payment.PaymentResult;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
public class VirtualPaymentGateway implements PaymentGateway {

    private final SecureRandom random = new SecureRandom();

    @Override
    public PaymentResult authorize(long amount, String orderId) {
        // 10% 확률로 실패 시뮬레이션
        int r = random.nextInt(100);
        if (r < 10) {
            return PaymentResult.failure("VIRTUAL_GATEWAY_RANDOM_FAIL");
        }
        // 성공
        String txId = "VT-" + UUID.randomUUID();
        return PaymentResult.success(txId, amount);
    }
}
