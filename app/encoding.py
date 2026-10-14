"""Short-code generation: Base62 encoding of a scrambled database ID.

Every row gets an auto-increment integer ID from the database. That ID is passed
through a reversible permutation of the code space, then written in Base62 and
padded to a fixed length:

    x = (id * multiplier + offset) mod 62**length        # affine step
    x = reverse the Base62 digits of x                    # moves low digits high
    x = (x  * multiplier + offset) mod 62**length        # affine step again
    code = base62(x), left-padded to `length` characters

Each step is a bijection (the multiplier is coprime with 62**length, and digit
reversal on a fixed width is its own inverse), so the whole mapping is a
bijection: every ID gets exactly one code and no two IDs ever share one. There
is no collision check and no retry loop. Consecutive IDs give codes that look
unrelated, which hides how many links exist and stops casual enumeration.

This is obfuscation, not cryptography. Someone who collects many codes and
knows the algorithm could recover the constants. For links that must be
unguessable, use random tokens instead (see docs/DESIGN.md).
"""

from math import gcd

ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
BASE = len(ALPHABET)  # 62
_INDEX = {char: i for i, char in enumerate(ALPHABET)}


def base62_encode(number: int, length: int) -> str:
    """Encode a non-negative integer as Base62, left-padded to `length`."""
    if number < 0:
        raise ValueError("number must be non-negative")
    chars = []
    while number:
        number, remainder = divmod(number, BASE)
        chars.append(ALPHABET[remainder])
    encoded = "".join(reversed(chars)) or ALPHABET[0]
    if len(encoded) > length:
        raise ValueError(f"number does not fit in {length} Base62 characters")
    return encoded.rjust(length, ALPHABET[0])


def base62_decode(code: str) -> int:
    """Decode a Base62 string back to an integer."""
    number = 0
    for char in code:
        if char not in _INDEX:
            raise ValueError(f"invalid Base62 character: {char!r}")
        number = number * BASE + _INDEX[char]
    return number


class CodeGenerator:
    """Turns database IDs into fixed-length, non-sequential short codes."""

    def __init__(self, length: int, multiplier: int, offset: int) -> None:
        self.length = length
        self.space = BASE**length
        if gcd(multiplier, self.space) != 1:
            raise ValueError(
                "id_scramble_multiplier must be coprime with 62**code_length "
                "(odd and not divisible by 31)"
            )
        self.multiplier = multiplier % self.space
        self.offset = offset % self.space
        self._inverse = pow(self.multiplier, -1, self.space)

    @property
    def capacity(self) -> int:
        """How many distinct codes this generator can produce."""
        return self.space

    def _affine(self, x: int) -> int:
        return (x * self.multiplier + self.offset) % self.space

    def _affine_inverse(self, x: int) -> int:
        return ((x - self.offset) * self._inverse) % self.space

    def _reverse_digits(self, x: int) -> int:
        return base62_decode(base62_encode(x, self.length)[::-1])

    def encode(self, row_id: int) -> str:
        if not 0 <= row_id < self.space:
            raise ValueError("ID is outside the code space; increase code_length")
        x = self._affine(self._reverse_digits(self._affine(row_id)))
        return base62_encode(x, self.length)

    def decode(self, code: str) -> int:
        """Inverse of `encode`. Used by tests to prove the mapping is a bijection."""
        x = base62_decode(code)
        return self._affine_inverse(self._reverse_digits(self._affine_inverse(x)))

    def is_generated_shape(self, code: str) -> bool:
        """True if `code` looks like something `encode` could produce."""
        return len(code) == self.length and all(char in _INDEX for char in code)
