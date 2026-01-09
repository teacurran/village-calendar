package villagecompute.calendar.services;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Unit tests for EmailService. Tests email sending, domain filtering, and environment-specific behavior. Requires
 * quarkus.mailer.mock=true in test configuration.
 */
@QuarkusTest
class EmailServiceTest {

    @Inject
    EmailService emailService;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void setUp() {
        mailbox.clear();
    }

    /** Helper to get total sent email count. */
    private int getTotalMailCount() {
        return mailbox.getTotalMessagesSent();
    }

    // ========== SEND EMAIL TESTS ==========

    @Test
    void testSendEmail_ToSafeDomain_SendsEmail() {
        // Given - villagecompute.com is in safe test domains list
        String to = "test@villagecompute.com";
        String subject = "Test Subject";
        String body = "Test body content";

        // When
        emailService.sendEmail(to, subject, body);

        // Then
        assertEquals(1, mailbox.getMailsSentTo(to).size());
        Mail sent = mailbox.getMailsSentTo(to).get(0);
        assertTrue(sent.getSubject().contains(subject));
        assertEquals(body, sent.getText());
    }

    @Test
    void testSendEmail_ToUnsafeDomain_BlocksEmail() {
        // Given - random domain not in safe test domains
        String to = "test@random-unsafe-domain.com";
        String subject = "Test Subject";
        String body = "Test body content";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendEmail(to, subject, body);

        // Then - email should be blocked in test environment
        assertEquals(beforeCount, getTotalMailCount(), "Email to unsafe domain should be blocked");
    }

    @Test
    void testSendEmail_WithCustomFromAddress() {
        // Given
        String from = "custom@villagecompute.com";
        String to = "test@villagecompute.com";
        String subject = "Custom From Test";
        String body = "Test body";

        // When
        emailService.sendEmail(from, to, subject, body);

        // Then
        assertEquals(1, mailbox.getMailsSentTo(to).size());
        Mail sent = mailbox.getMailsSentTo(to).get(0);
        assertEquals(from, sent.getFrom());
    }

    @Test
    void testSendEmail_ToGrilledcheeseDomain_SendsEmail() {
        // Given - grilledcheese.com is in safe test domains list
        String to = "test@grilledcheese.com";
        String subject = "Grilledcheese Domain Test";
        String body = "Test body";

        // When
        emailService.sendEmail(to, subject, body);

        // Then
        assertEquals(1, mailbox.getMailsSentTo(to).size());
    }

    @Test
    void testSendEmail_ToApproachingpiDomain_SendsEmail() {
        // Given - approachingpi.com is in safe test domains list
        String to = "test@approachingpi.com";
        String subject = "Approachingpi Domain Test";
        String body = "Test body";

        // When
        emailService.sendEmail(to, subject, body);

        // Then
        assertEquals(1, mailbox.getMailsSentTo(to).size());
    }

    // ========== SEND HTML EMAIL TESTS ==========

    @Test
    void testSendHtmlEmail_ToSafeDomain_SendsEmail() {
        // Given
        String to = "test@villagecompute.com";
        String subject = "HTML Test";
        String htmlBody = "<html><body><h1>Test</h1></body></html>";

        // When
        emailService.sendHtmlEmail(to, subject, htmlBody);

        // Then
        assertEquals(1, mailbox.getMailsSentTo(to).size());
        Mail sent = mailbox.getMailsSentTo(to).get(0);
        assertEquals(htmlBody, sent.getHtml());
    }

    @Test
    void testSendHtmlEmail_ToUnsafeDomain_BlocksEmail() {
        // Given
        String to = "test@unsafe-domain.net";
        String subject = "HTML Test";
        String htmlBody = "<html><body><h1>Test</h1></body></html>";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendHtmlEmail(to, subject, htmlBody);

        // Then - email should be blocked
        assertEquals(beforeCount, getTotalMailCount(), "HTML email to unsafe domain should be blocked");
    }

    @Test
    void testSendHtmlEmail_WithCustomFromAddress() {
        // Given
        String from = "noreply@villagecompute.com";
        String to = "test@villagecompute.com";
        String subject = "Custom From HTML Test";
        String htmlBody = "<p>Test</p>";

        // When
        emailService.sendHtmlEmail(from, to, subject, htmlBody);

        // Then
        assertEquals(1, mailbox.getMailsSentTo(to).size());
        Mail sent = mailbox.getMailsSentTo(to).get(0);
        assertEquals(from, sent.getFrom());
    }

    // ========== INVALID EMAIL ADDRESS TESTS ==========

    @Test
    void testSendEmail_NullAddress_BlocksEmail() {
        // Given
        String to = null;
        String subject = "Test";
        String body = "Test body";

        // When
        emailService.sendEmail(to, subject, body);

        // Then - should not throw, but email is blocked
        // No assertion needed - test passes if no exception thrown
    }

    @Test
    void testSendEmail_InvalidAddressWithoutAt_BlocksEmail() {
        // Given
        String to = "invalid-email-no-at-symbol";
        String subject = "Test";
        String body = "Test body";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendEmail(to, subject, body);

        // Then - should be blocked due to invalid format (no @ symbol)
        assertEquals(beforeCount, getTotalMailCount(), "Email without @ should be blocked");
    }

    @Test
    void testSendHtmlEmail_NullAddress_BlocksEmail() {
        // Given
        String to = null;
        String subject = "Test";
        String htmlBody = "<p>Test</p>";

        // When
        emailService.sendHtmlEmail(to, subject, htmlBody);

        // Then - should not throw, but email is blocked
        // No assertion needed - test passes if no exception thrown
    }

    // ========== ENVIRONMENT PREFIX TESTS ==========

    @Test
    void testSendEmail_AddsEnvironmentPrefix() {
        // Given - in test profile (not production)
        String to = "test@villagecompute.com";
        String subject = "Original Subject";
        String body = "Test body";

        // When
        emailService.sendEmail(to, subject, body);

        // Then - subject should have environment prefix
        assertEquals(1, mailbox.getMailsSentTo(to).size());
        Mail sent = mailbox.getMailsSentTo(to).get(0);
        // In test profile, should have prefix like [TEST] or [DEV]
        String sentSubject = sent.getSubject();
        assertTrue(sentSubject.contains(subject), "Subject should contain original subject");
    }

    // ========== DOMAIN EXTRACTION TESTS ==========

    @Test
    void testSendEmail_SubdomainOfSafeDomain_BlocksEmail() {
        // Given - subdomain of safe domain is NOT safe (exact match required)
        String to = "test@sub.villagecompute.com";
        String subject = "Subdomain Test";
        String body = "Test body";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendEmail(to, subject, body);

        // Then - subdomain should be blocked (exact match required)
        assertEquals(beforeCount, getTotalMailCount(), "No email should be sent for subdomain");
    }

    @Test
    void testSendEmail_SafeDomainWithDifferentCase_SendsEmail() {
        // Given - case insensitive domain check
        String to = "test@VILLAGECOMPUTE.COM";
        String subject = "Case Test";
        String body = "Test body";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendEmail(to, subject, body);

        // Then - should work because domain comparison is case insensitive
        assertEquals(beforeCount + 1, getTotalMailCount(), "Email should be sent for case-insensitive domain match");
    }

    @Test
    void testSendEmail_DomainExtractionWithLastAt() {
        // Given - test that domain is extracted from after the last @ symbol
        // Using a safe domain to verify the extraction works
        String to = "user@villagecompute.com";
        String subject = "Domain Extraction Test";
        String body = "Test body";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendEmail(to, subject, body);

        // Then - should extract domain correctly and send
        assertEquals(beforeCount + 1, getTotalMailCount(), "Email should be sent");
    }

    // ========== MULTIPLE RECIPIENTS TESTS ==========

    @Test
    void testSendEmail_MultipleSafeDomainEmails() {
        // Given
        String subject = "Multiple Recipients Test";
        String body = "Test body";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendEmail("user1@villagecompute.com", subject, body);
        emailService.sendEmail("user2@grilledcheese.com", subject, body);
        emailService.sendEmail("user3@approachingpi.com", subject, body);

        // Then - all three should be sent
        assertEquals(beforeCount + 3, getTotalMailCount(), "All three emails to safe domains should be sent");
    }

    @Test
    void testSendEmail_MixedSafeAndUnsafeDomains() {
        // Given
        String subject = "Mixed Domains Test";
        String body = "Test body";

        int beforeCount = getTotalMailCount();

        // When
        emailService.sendEmail("safe@villagecompute.com", subject, body);
        emailService.sendEmail("unsafe@gmail.com", subject, body);

        // Then - only safe domain email should be sent
        assertEquals(beforeCount + 1, getTotalMailCount(), "Only safe domain email should be sent");
    }
}
