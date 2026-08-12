package zw.ac.uz.emhare.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.services.s3.S3Client;
import zw.ac.uz.emhare.notifications.domain.model.NotificationRequest;
import zw.ac.uz.emhare.notifications.domain.model.NotificationTemplate;
import zw.ac.uz.emhare.notifications.infrastructure.persistence.NotificationRequestAttachmentRepository;

/** @author Tinashe K */
class SmtpNotificationDeliveryProviderTest {

    @Test
    void sendsEmailUsingConfiguredAddressAndDisplayName() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        NotificationRequestAttachmentRepository attachmentRepository =
                mock(NotificationRequestAttachmentRepository.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        when(attachmentRepository.findAllByNotificationRequestIdOrderByAttachmentSequenceAsc(null))
                .thenReturn(List.of());
        SmtpNotificationDeliveryProvider provider = new SmtpNotificationDeliveryProvider(
                mailSender,
                attachmentRepository,
                mock(S3Client.class),
                "smtp@admin.uz.ac.zw",
                "University of Zimbabwe");

        NotificationTemplate template = new NotificationTemplate(
                "SMTP_TEST",
                1,
                "SMTP test",
                "SMTP_TEST",
                NotificationTemplate.Channel.EMAIL,
                NotificationTemplate.Category.TRANSACTIONAL,
                "en-ZW",
                "Test subject",
                "Test body",
                UUID.fromString("50000000-0000-4000-8000-000000000001"));
        NotificationRequest request = new NotificationRequest(
                "NTF-SMTP-TEST",
                "smtp-test",
                "notifications-service",
                null,
                "SMTP_TEST",
                template,
                null,
                "recipient@example.test",
                "recipient@example.test",
                "Test subject",
                "Test body",
                NotificationRequest.Priority.NORMAL,
                "NOT_REQUIRED",
                Instant.now(),
                1,
                false);

        NotificationDeliveryProvider.DeliveryResult result = provider.deliver(request);

        assertTrue(result.sent());
        InternetAddress sender = (InternetAddress) message.getFrom()[0];
        assertEquals("smtp@admin.uz.ac.zw", sender.getAddress());
        assertEquals("University of Zimbabwe", sender.getPersonal());
        verify(mailSender).send(message);
    }
}
