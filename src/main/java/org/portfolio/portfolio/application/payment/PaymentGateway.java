package org.portfolio.portfolio.application.payment;

public interface PaymentGateway {
    PaymentResult authorize(long amount, String orderId);
}
