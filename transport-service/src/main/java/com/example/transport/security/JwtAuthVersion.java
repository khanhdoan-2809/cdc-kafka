package com.example.transport.security;

import java.math.BigInteger;
import java.util.OptionalLong;

public final class JwtAuthVersion {
    private JwtAuthVersion() {}

    public static OptionalLong read(Object claim) {
        long value;
        if (claim instanceof Byte
                || claim instanceof Short
                || claim instanceof Integer
                || claim instanceof Long) {
            value = ((Number) claim).longValue();
        } else if (claim instanceof BigInteger integer
                && integer.signum() >= 0
                && integer.bitLength() <= 63) {
            value = integer.longValueExact();
        } else return OptionalLong.empty();
        return value >= 0 ? OptionalLong.of(value) : OptionalLong.empty();
    }
}
