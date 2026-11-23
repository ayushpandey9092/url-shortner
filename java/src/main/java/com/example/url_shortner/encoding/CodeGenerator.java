package com.example.url_shortner.encoding;

import java.math.BigInteger;

public final class CodeGenerator {

    int length;
    long multiplier;
    long offset;

    // The size of the code space, 62^length, and the values the steps need.
    private final BigInteger space;
    private final BigInteger bigMultiplier;
    private final BigInteger bigOffset;
    private final BigInteger inverseMultiplier;

    public CodeGenerator(int length, long multiplier, long offset) {
        this.space = BigInteger.valueOf(62).pow(length);
        if (space.gcd(BigInteger.valueOf(multiplier)).intValue() != 1) {
            throw new IllegalArgumentException("Multiplier must be coprime with 62");
        }
        this.length = length;
        this.multiplier = multiplier;
        this.offset = offset;
        this.bigMultiplier = BigInteger.valueOf(multiplier);
        this.bigOffset = BigInteger.valueOf(offset);
        // modInverse finds m' with multiplier * m' = 1 (mod space); decode needs it.
        this.inverseMultiplier = bigMultiplier.modInverse(space);
    }

    public String encode(long id) {
        if (id < 0 || BigInteger.valueOf(id).compareTo(space) >= 0) {
            throw new IllegalArgumentException("ID is outside the code space; increase code length");
        }
        long x = affine(id);
        x = reverseDigits(x);
        x = affine(x);
        return Base62.encode(x, length);
    }

    public long decode(String code) {
        long x = Base62.decode(code);
        x = affineInverse(x);
        x = reverseDigits(x);
        x = affineInverse(x);
        return x;
    }

    // Step: x * multiplier + offset, mod space.
    private long affine(long x) {
        return BigInteger.valueOf(x)
                .multiply(bigMultiplier)
                .add(bigOffset)
                .mod(space)
                .longValue();
    }

    // Undo of affine: (x - offset) * inverse, mod space.
    private long affineInverse(long x) {
        return BigInteger.valueOf(x)
                .subtract(bigOffset)
                .multiply(inverseMultiplier)
                .mod(space)
                .longValue();
    }

    // Step: write x in Base62, reverse the characters, read the result back as a number.
    private long reverseDigits(long x) {
        String digits = Base62.encode(x, length);
        return Base62.decode(new StringBuilder(digits).reverse().toString());
    }
}
