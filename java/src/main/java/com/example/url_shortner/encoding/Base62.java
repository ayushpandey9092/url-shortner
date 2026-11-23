package com.example.url_shortner.encoding;

public final class Base62 {

    private Base62() {
    }
    private static char encodeDigit(int digit) {
        if (digit < 10) {
            return (char) ('0' + digit);
        } else if (digit < 36) {
            return (char) ('a' + digit - 10);
        } else {
            return (char) ('A' + digit - 36);
        }
    }
    public static String encode(long number, int length) {
        StringBuilder result = new StringBuilder();
        if(length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }
        if(number<0 || (length > 0 && number >= Math.pow(62, length))) {
            throw new IllegalArgumentException("Number too large to encode in the specified length");
        }
        while(length-- > 0) {
            result.append(encodeDigit((int)(number % 62)));
            number /= 62;
        }
        return result.reverse().toString();
    }

    public static long decode(String base62) {
        long result = 0;
        for (int i = 0; i < base62.length(); i++) {
            char c = base62.charAt(i);
            int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'a' && c <= 'z') {
                digit = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'Z') {
                digit = c - 'A' + 36;
            } else {
                throw new IllegalArgumentException("Invalid character in base62 string: " + c);
            }
            result = result * 62 + digit;
        }
        return result;
    }
}
