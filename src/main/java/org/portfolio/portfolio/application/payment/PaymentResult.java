package org.portfolio.portfolio.application.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentResult {
    private final boolean success;
    private final String transactionId;
    private final long approvedAmount;
    private final String failureReason;

    public static PaymentResult success(String transactionId, long approvedAmount) {
        return new PaymentResult(true, transactionId, approvedAmount, null);
    }

    public static PaymentResult failure(String reason) {
        return new PaymentResult(false, null, 0L, reason);
    }
}
