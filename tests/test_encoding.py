import pytest

from app.encoding import ALPHABET, CodeGenerator, base62_decode, base62_encode


def test_base62_round_trip():
    for number in [0, 1, 61, 62, 3843, 3844, 10**9, 62**7 - 1]:
        assert base62_decode(base62_encode(number, 7)) == number


def test_base62_pads_to_length():
    assert base62_encode(0, 7) == "0000000"
    assert base62_encode(61, 3) == "00Z"


def test_base62_rejects_overflow():
    with pytest.raises(ValueError):
        base62_encode(62**3, 3)


def test_generator_is_a_bijection_on_a_small_space():
    # With length 3 the whole space (238,328 codes) can be checked exhaustively.
    gen = CodeGenerator(length=3, multiplier=2_147_483_647, offset=12345)
    codes = {gen.encode(i) for i in range(gen.capacity)}
    assert len(codes) == gen.capacity


def test_generator_round_trip_and_fixed_length():
    gen = CodeGenerator(length=7, multiplier=2_147_483_647, offset=916_132_831)
    for row_id in [1, 2, 3, 1000, 123_456_789, gen.capacity - 1]:
        code = gen.encode(row_id)
        assert len(code) == 7
        assert all(c in ALPHABET for c in code)
        assert gen.decode(code) == row_id


def test_consecutive_ids_do_not_give_sequential_codes():
    gen = CodeGenerator(length=7, multiplier=2_176_477_521_981, offset=1_094_865_271)
    codes = [gen.encode(i) for i in range(1, 101)]
    # Neighbouring codes should rarely share a first character or a last character.
    same_first = sum(a[0] == b[0] for a, b in zip(codes, codes[1:], strict=False))
    same_last = sum(a[-1] == b[-1] for a, b in zip(codes, codes[1:], strict=False))
    assert same_first < 10
    assert same_last < 10
    assert codes != sorted(codes)


@pytest.mark.parametrize("multiplier", [62, 31 * 7, 2])
def test_generator_rejects_non_coprime_multiplier(multiplier):
    with pytest.raises(ValueError):
        CodeGenerator(length=7, multiplier=multiplier, offset=0)


def test_generated_shape_detection():
    gen = CodeGenerator(length=7, multiplier=2_147_483_647, offset=0)
    assert gen.is_generated_shape("aB3x9Kz")
    assert not gen.is_generated_shape("my-link")
    assert not gen.is_generated_shape("short")
