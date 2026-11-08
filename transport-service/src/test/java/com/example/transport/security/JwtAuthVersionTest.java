package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

class JwtAuthVersionTest {
    @Test
    void acceptsOnlyNonnegativeIntegersInDatabaseRange() {
        for (Object good : List.of(0, 1L, Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE)))
            assertThat(JwtAuthVersion.read(good)).isPresent();
        for (Object bad :
                Arrays.asList(
                        null,
                        -1,
                        -1L,
                        1.0,
                        1.5,
                        "1",
                        true,
                        Map.of(),
                        List.of(),
                        BigInteger.ONE.shiftLeft(63)))
            assertThat(JwtAuthVersion.read(bad)).isEmpty();
    }
}
