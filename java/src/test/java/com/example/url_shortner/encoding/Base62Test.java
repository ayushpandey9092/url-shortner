package com.example.url_shortner.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62Test {

    @Test
    void encodesZeroAsPaddedZeros() {
        assertEquals("0000000", Base62.encode(0, 7));
    }
    @Test
    void encodeIllegalLength() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(62*62*62, 3));
    }

    @Test
    void encodeNegativeNumber() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1, 7));
    }

    @Test
    void decodesBase62String() {
        assertEquals(25, Base62.decode("00p"));
    }

    @Test
    void decodeRejectsInvalidCharacter() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("0-p"));
    }
}
