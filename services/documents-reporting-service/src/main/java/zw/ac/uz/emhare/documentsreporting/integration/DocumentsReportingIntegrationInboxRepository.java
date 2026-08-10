package zw.ac.uz.emhare.documentsreporting.integration;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface DocumentsReportingIntegrationInboxRepository
        extends JpaRepository<DocumentsReportingIntegrationInbox, UUID> {
}
