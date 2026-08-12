package zw.ac.uz.emhare.notifications;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import zw.ac.uz.emhare.notifications.domain.model.NotificationRequest;
import zw.ac.uz.emhare.notifications.infrastructure.persistence.NotificationRequestAttachmentRepository;

/** SMTP delivery with checksum-validated S3 offer-letter attachments. @author Tinashe K */
@Component
@ConditionalOnProperty(name = "emhare.notifications.provider", havingValue = "smtp")
public class SmtpNotificationDeliveryProvider implements NotificationDeliveryProvider {
    private final JavaMailSender mailSender;
    private final NotificationRequestAttachmentRepository attachmentRepository;
    private final S3Client s3Client;
    private final String fromAddress;
    private final String fromName;

    public SmtpNotificationDeliveryProvider(JavaMailSender mailSender,
            NotificationRequestAttachmentRepository attachmentRepository, S3Client s3Client,
            @Value("${emhare.notifications.smtp.from-address}") String fromAddress,
            @Value("${emhare.notifications.smtp.from-name}") String fromName) {
        this.mailSender = mailSender;
        this.attachmentRepository = attachmentRepository;
        this.s3Client = s3Client;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override public String code() { return "SMTP"; }

    @Override
    public DeliveryResult deliver(NotificationRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(request.getRecipientAddress());
            helper.setSubject(request.getSubject() == null ? "eMhare notification" : request.getSubject());
            helper.setText(request.getBody(), false);
            var attachments = attachmentRepository.findAllByNotificationRequestIdOrderByAttachmentSequenceAsc(request.getId());
            for (var attachment : attachments) {
                URI uri = URI.create(attachment.getStorageUri());
                String bucket = uri.getHost();
                String key = uri.getPath().substring(1);
                byte[] bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
                String actualChecksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!actualChecksum.equalsIgnoreCase(attachment.getChecksumSha256())) {
                    return DeliveryResult.failed(false, "ATTACHMENT_CHECKSUM_MISMATCH",
                            "Stored attachment checksum does not match its immutable reference.",
                            Map.of("documentId", attachment.getSourceDocumentId().toString()));
                }
                helper.addAttachment(attachment.getFileName(), new ByteArrayResource(bytes), attachment.getContentType());
            }
            mailSender.send(message);
            return DeliveryResult.sent("smtp-" + UUID.randomUUID(), Map.of("attachmentCount", attachments.size()));
        } catch (Exception exception) {
            String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return DeliveryResult.failed(true, "SMTP_DELIVERY_FAILED", detail, Map.of());
        }
    }
}
