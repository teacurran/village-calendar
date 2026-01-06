package villagecompute.calendar.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Tests for the API type classes.
 */
class TypesTest {

    // ============================================================================
    // ErrorType Tests
    // ============================================================================

    @Test
    void errorType_RecordAccessor_ReturnsValue() {
        ErrorType error = new ErrorType("Something went wrong");
        assertEquals("Something went wrong", error.error());
    }

    @Test
    void errorType_Of_CreatesInstance() {
        ErrorType error = ErrorType.of("Test error message");
        assertEquals("Test error message", error.error());
    }

    @Test
    void errorType_Equality() {
        ErrorType error1 = ErrorType.of("Same message");
        ErrorType error2 = new ErrorType("Same message");
        assertEquals(error1, error2);
    }

    // ============================================================================
    // SuccessType Tests
    // ============================================================================

    @Test
    void successType_RecordAccessor_ReturnsValue() {
        SuccessType success = new SuccessType("success");
        assertEquals("success", success.status());
    }

    @Test
    void successType_Ok_ReturnsSingletonInstance() {
        SuccessType success1 = SuccessType.ok();
        SuccessType success2 = SuccessType.ok();
        assertSame(success1, success2);
    }

    @Test
    void successType_Ok_HasSuccessStatus() {
        SuccessType success = SuccessType.ok();
        assertEquals("success", success.status());
    }

    // ============================================================================
    // PaymentIntentType Tests
    // ============================================================================

    @Test
    void paymentIntentType_FieldsAssignable() {
        PaymentIntentType intent = new PaymentIntentType();
        intent.id = "pi_test123";
        intent.clientSecret = "secret_abc";
        intent.amount = 2999;
        intent.calendarId = UUID.randomUUID();
        intent.quantity = 2;
        intent.status = "succeeded";

        assertEquals("pi_test123", intent.id);
        assertEquals("secret_abc", intent.clientSecret);
        assertEquals(2999, intent.amount);
        assertNotNull(intent.calendarId);
        assertEquals(2, intent.quantity);
        assertEquals("succeeded", intent.status);
    }

    @Test
    void paymentIntentType_FromCheckoutSession_CreatesInstance() {
        UUID calendarId = UUID.randomUUID();
        PaymentIntentType intent = PaymentIntentType.fromCheckoutSession("cs_test123",
                "https://checkout.stripe.com/xxx", 2999, calendarId, 1);

        assertEquals("cs_test123", intent.id);
        assertEquals("https://checkout.stripe.com/xxx", intent.clientSecret);
        assertEquals(2999, intent.amount);
        assertEquals(calendarId, intent.calendarId);
        assertEquals(1, intent.quantity);
        assertEquals("requires_payment_method", intent.status);
    }

    @Test
    void paymentIntentType_FromCheckoutSession_WithNullCalendarId() {
        PaymentIntentType intent = PaymentIntentType.fromCheckoutSession("cs_test123",
                "https://checkout.stripe.com/xxx", 1500, null, 3);

        assertEquals("cs_test123", intent.id);
        assertNull(intent.calendarId);
        assertEquals(3, intent.quantity);
    }

    // ============================================================================
    // DisplaySettingsType Tests
    // ============================================================================

    @Test
    void displaySettingsType_Equals_SameValues_ReturnsTrue() {
        DisplaySettingsType settings1 = new DisplaySettingsType();
        settings1.emojiSize = 24;
        settings1.emojiX = 10.5;
        settings1.emojiY = 20.5;
        settings1.textSize = 12;
        settings1.textX = 5.0;
        settings1.textY = 15.0;
        settings1.textRotation = 45.0;
        settings1.textColor = "#FF0000";
        settings1.textAlign = "center";
        settings1.textBold = true;
        settings1.textWrap = false;

        DisplaySettingsType settings2 = new DisplaySettingsType();
        settings2.emojiSize = 24;
        settings2.emojiX = 10.5;
        settings2.emojiY = 20.5;
        settings2.textSize = 12;
        settings2.textX = 5.0;
        settings2.textY = 15.0;
        settings2.textRotation = 45.0;
        settings2.textColor = "#FF0000";
        settings2.textAlign = "center";
        settings2.textBold = true;
        settings2.textWrap = false;

        assertEquals(settings1, settings2);
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void displaySettingsType_Equals_DifferentEmojiSize_ReturnsFalse() {
        DisplaySettingsType settings1 = new DisplaySettingsType();
        settings1.emojiSize = 24;

        DisplaySettingsType settings2 = new DisplaySettingsType();
        settings2.emojiSize = 32;

        assertNotEquals(settings1, settings2);
    }

    @Test
    void displaySettingsType_Equals_DifferentTextColor_ReturnsFalse() {
        DisplaySettingsType settings1 = new DisplaySettingsType();
        settings1.textColor = "#FF0000";

        DisplaySettingsType settings2 = new DisplaySettingsType();
        settings2.textColor = "#00FF00";

        assertNotEquals(settings1, settings2);
    }

    @Test
    void displaySettingsType_Equals_NullFields_ReturnsTrue() {
        DisplaySettingsType settings1 = new DisplaySettingsType();
        DisplaySettingsType settings2 = new DisplaySettingsType();

        assertEquals(settings1, settings2);
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void displaySettingsType_Equals_SameInstance_ReturnsTrue() {
        DisplaySettingsType settings = new DisplaySettingsType();
        settings.emojiSize = 24;

        assertEquals(settings, settings);
    }

    @Test
    void displaySettingsType_Equals_Null_ReturnsFalse() {
        DisplaySettingsType settings = new DisplaySettingsType();

        assertNotEquals(settings, null);
    }

    @Test
    void displaySettingsType_Equals_DifferentClass_ReturnsFalse() {
        DisplaySettingsType settings = new DisplaySettingsType();

        assertNotEquals(settings, "not a DisplaySettingsType");
    }

    @Test
    void displaySettingsType_HashCode_ConsistentWithEquals() {
        DisplaySettingsType settings1 = new DisplaySettingsType();
        settings1.textBold = true;
        settings1.textWrap = true;

        DisplaySettingsType settings2 = new DisplaySettingsType();
        settings2.textBold = true;
        settings2.textWrap = true;

        // Equal objects must have equal hash codes
        assertEquals(settings1, settings2);
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    // ============================================================================
    // HolidayType Tests
    // ============================================================================

    @Test
    void holidayType_Equals_SameValues_ReturnsTrue() {
        HolidayType holiday1 = new HolidayType("Christmas", "🎄");
        HolidayType holiday2 = new HolidayType("Christmas", "🎄");

        assertEquals(holiday1, holiday2);
        assertEquals(holiday1.hashCode(), holiday2.hashCode());
    }

    @Test
    void holidayType_Equals_DifferentName_ReturnsFalse() {
        HolidayType holiday1 = new HolidayType("Christmas", "🎄");
        HolidayType holiday2 = new HolidayType("New Year", "🎄");

        assertNotEquals(holiday1, holiday2);
    }

    @Test
    void holidayType_Equals_DifferentEmoji_ReturnsFalse() {
        HolidayType holiday1 = new HolidayType("Christmas", "🎄");
        HolidayType holiday2 = new HolidayType("Christmas", "🎅");

        assertNotEquals(holiday1, holiday2);
    }

    @Test
    void holidayType_Equals_NullEmoji_ReturnsTrue() {
        HolidayType holiday1 = new HolidayType("Independence Day");
        HolidayType holiday2 = new HolidayType("Independence Day");

        assertEquals(holiday1, holiday2);
        assertEquals(holiday1.hashCode(), holiday2.hashCode());
    }

    @Test
    void holidayType_Equals_SameInstance_ReturnsTrue() {
        HolidayType holiday = new HolidayType("Christmas", "🎄");

        assertEquals(holiday, holiday);
    }

    @Test
    void holidayType_Equals_Null_ReturnsFalse() {
        HolidayType holiday = new HolidayType("Christmas", "🎄");

        assertNotEquals(holiday, null);
    }

    @Test
    void holidayType_Equals_DifferentClass_ReturnsFalse() {
        HolidayType holiday = new HolidayType("Christmas", "🎄");

        assertNotEquals(holiday, "not a HolidayType");
    }

    @Test
    void holidayType_DefaultConstructor_AllFieldsNull() {
        HolidayType holiday = new HolidayType();

        assertNull(holiday.name);
        assertNull(holiday.emoji);
    }

    // ============================================================================
    // CustomDateEntryType Tests
    // ============================================================================

    @Test
    void customDateEntryType_Equals_SameValues_ReturnsTrue() {
        DisplaySettingsType settings = new DisplaySettingsType();
        settings.emojiSize = 24;

        CustomDateEntryType entry1 = new CustomDateEntryType("🎂", "Birthday", settings);
        CustomDateEntryType entry2 = new CustomDateEntryType("🎂", "Birthday", settings);

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
    }

    @Test
    void customDateEntryType_Equals_DifferentEmoji_ReturnsFalse() {
        CustomDateEntryType entry1 = new CustomDateEntryType("🎂", "Birthday");
        CustomDateEntryType entry2 = new CustomDateEntryType("🎉", "Birthday");

        assertNotEquals(entry1, entry2);
    }

    @Test
    void customDateEntryType_Equals_DifferentTitle_ReturnsFalse() {
        CustomDateEntryType entry1 = new CustomDateEntryType("🎂", "Birthday");
        CustomDateEntryType entry2 = new CustomDateEntryType("🎂", "Anniversary");

        assertNotEquals(entry1, entry2);
    }

    @Test
    void customDateEntryType_Equals_DifferentDisplaySettings_ReturnsFalse() {
        DisplaySettingsType settings1 = new DisplaySettingsType();
        settings1.emojiSize = 24;

        DisplaySettingsType settings2 = new DisplaySettingsType();
        settings2.emojiSize = 32;

        CustomDateEntryType entry1 = new CustomDateEntryType("🎂", "Birthday", settings1);
        CustomDateEntryType entry2 = new CustomDateEntryType("🎂", "Birthday", settings2);

        assertNotEquals(entry1, entry2);
    }

    @Test
    void customDateEntryType_Equals_NullDisplaySettings_ReturnsTrue() {
        CustomDateEntryType entry1 = new CustomDateEntryType("🎂", "Birthday");
        CustomDateEntryType entry2 = new CustomDateEntryType("🎂", "Birthday");

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
    }

    @Test
    void customDateEntryType_Equals_SameInstance_ReturnsTrue() {
        CustomDateEntryType entry = new CustomDateEntryType("🎂", "Birthday");

        assertEquals(entry, entry);
    }

    @Test
    void customDateEntryType_Equals_Null_ReturnsFalse() {
        CustomDateEntryType entry = new CustomDateEntryType("🎂", "Birthday");

        assertNotEquals(entry, null);
    }

    @Test
    void customDateEntryType_Equals_DifferentClass_ReturnsFalse() {
        CustomDateEntryType entry = new CustomDateEntryType("🎂", "Birthday");

        assertNotEquals(entry, "not a CustomDateEntryType");
    }

    @Test
    void customDateEntryType_EmojiOnlyConstructor() {
        CustomDateEntryType entry = new CustomDateEntryType("🎂");

        assertEquals("🎂", entry.emoji);
        assertNull(entry.title);
        assertNull(entry.displaySettings);
    }

    @Test
    void customDateEntryType_EmojiAndSettingsConstructor() {
        DisplaySettingsType settings = new DisplaySettingsType();
        settings.emojiSize = 24;

        CustomDateEntryType entry = new CustomDateEntryType("🎂", settings);

        assertEquals("🎂", entry.emoji);
        assertNull(entry.title);
        assertEquals(settings, entry.displaySettings);
    }
}
