package com.prc.projectcell.ae2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class SafeLongArithmeticTest {
    // More EMC than fits in a long (> 9.2e18), a normal late-game balance.
    private static final BigInteger HUGE_EMC = new BigInteger("30000000000000000000");

    @Test
    void smallDividendUsesExactLongDivision() {
        assertEquals(333L, SafeLongArithmetic.safeDivide(BigInteger.valueOf(1000), 3));
    }

    @Test
    void quotientBeyondLongRangeIsClampedNotTruncated() {
        // Before the fix, BigInteger.longValue() kept only the low 64 bits and returned
        // a negative count, so 1-EMC items vanished from the AE2 network.
        assertEquals(Long.MAX_VALUE, SafeLongArithmetic.safeDivide(HUGE_EMC, 1));
        assertEquals(Long.MAX_VALUE, SafeLongArithmetic.safeDivide(HUGE_EMC, 3));
    }

    @Test
    void hugeDividendWithQuotientInRangeIsExact() {
        assertEquals(7_500_000_000_000_000_000L, SafeLongArithmetic.safeDivide(HUGE_EMC, 4));
    }

    @Test
    void nonPositiveDivisorReturnsZero() {
        assertEquals(0L, SafeLongArithmetic.safeDivide(HUGE_EMC, 0));
        assertEquals(0L, SafeLongArithmetic.safeDivide(HUGE_EMC, -5));
    }

    @Test
    void divideUnsafeClampsWithBigDivisor() {
        BigInteger bigDivisor = BigInteger.ONE.shiftLeft(70);
        BigInteger enormous = BigInteger.ONE.shiftLeft(200);
        assertEquals(Long.MAX_VALUE, SafeLongArithmetic.divideUnsafe(enormous, bigDivisor));
    }
}
