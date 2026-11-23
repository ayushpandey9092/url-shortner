package com.example.url_shortner.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    @Test
    void rejectsMultiplierThatSharesAFactorWithTheCodeSpace() {
        assertThrows(IllegalArgumentException.class, () -> new CodeGenerator(7, 62, 0));
    }

    @Test
    void roundTripGivesFixedLengthCodesThatDecodeBackToTheId() {
        CodeGenerator gen = new CodeGenerator(7, 2_147_483_647L, 916_132_831L);
        long capacity = 3_521_614_606_208L; // 62^7
        long[] rowIds = {1, 2, 3, 1000, 123_456_789L, capacity - 1};

        for (long rowId : rowIds) {
            String code = gen.encode(rowId);
            assertEquals(7, code.length(), "code for id " + rowId + " should be 7 characters");
            assertEquals(rowId, gen.decode(code), "decode should return the original id");
        }
    }

    @Test
    void everyIdInASmallSpaceGetsADifferentCode() {
        // With length 3 the whole space is 62^3 = 238,328 codes, small enough to check them all.
        CodeGenerator gen = new CodeGenerator(3, 2_147_483_647L, 12345L);
        long capacity = 238_328L;

        Set<String> codes = new HashSet<>();
        for (long id = 0; id < capacity; id++) {
            codes.add(gen.encode(id));
        }

        assertEquals(capacity, codes.size(), "two different IDs produced the same code");
    }
}
