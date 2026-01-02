package villagecompute.calendar.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidUtilTest {

    @Test
    void parse_ValidUuid_ReturnsUuid() {
        UUID expected = UUID.randomUUID();
        UUID result = UuidUtil.parse(expected.toString(), "test ID");
        assertEquals(expected, result);
    }

    @Test
    void parse_InvalidUuid_ThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UuidUtil.parse("not-a-uuid", "test ID"));
        assertEquals("Invalid test ID format", ex.getMessage());
    }

    @Test
    void parse_NullValue_ThrowsNullPointerException() {
        // UUID.fromString throws NPE for null, propagated as-is
        assertThrows(NullPointerException.class, () -> UuidUtil.parse(null, "test ID"));
    }

    @Test
    void tryParse_ValidUuid_ReturnsOptionalWithUuid() {
        UUID expected = UUID.randomUUID();
        Optional<UUID> result = UuidUtil.tryParse(expected.toString());
        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @Test
    void tryParse_InvalidUuid_ReturnsEmpty() {
        Optional<UUID> result = UuidUtil.tryParse("not-a-uuid");
        assertTrue(result.isEmpty());
    }

    @Test
    void tryParse_NullValue_ReturnsEmpty() {
        Optional<UUID> result = UuidUtil.tryParse(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void tryParse_EmptyString_ReturnsEmpty() {
        Optional<UUID> result = UuidUtil.tryParse("");
        assertTrue(result.isEmpty());
    }

    @Test
    void isValid_ValidUuid_ReturnsTrue() {
        assertTrue(UuidUtil.isValid(UUID.randomUUID().toString()));
    }

    @Test
    void isValid_InvalidUuid_ReturnsFalse() {
        assertFalse(UuidUtil.isValid("not-a-uuid"));
    }

    @Test
    void isValid_NullValue_ReturnsFalse() {
        assertFalse(UuidUtil.isValid(null));
    }

    @Test
    void isValid_EmptyString_ReturnsFalse() {
        assertFalse(UuidUtil.isValid(""));
    }
}
