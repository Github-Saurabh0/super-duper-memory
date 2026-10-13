package com.saurabh.banking.fraud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FraudDetectorTest {

    private final FraudDetector detector = new FraudDetector();

    @Test
    void amountBelowOneLakhIsApproved() {
        assertEquals("APPROVED", detector.evaluate(99_999.99));
    }

    @Test
    void amountAtOneLakhIsMediumRisk() {
        assertEquals("MEDIUM_RISK", detector.evaluate(100_000));
    }

    @Test
    void amountBelowFiveLakhIsMediumRisk() {
        assertEquals("MEDIUM_RISK", detector.evaluate(499_999.99));
    }

    @Test
    void amountAtFiveLakhIsHighRisk() {
        assertEquals("HIGH_RISK", detector.evaluate(500_000));
    }

    @Test
    void amountAboveFiveLakhIsHighRisk() {
        assertEquals("HIGH_RISK", detector.evaluate(600_000));
    }

    @Test
    void negativeAmountIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> detector.evaluate(-1)
        );
    }

    @Test
    void nanAmountIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> detector.evaluate(Double.NaN)
        );
    }

    @Test
    void infiniteAmountIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> detector.evaluate(Double.POSITIVE_INFINITY)
        );
    }

}


