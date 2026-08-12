package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.messaging;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.DocumentsReportingIntegrationInbox;

import zw.ac.uz.emhare.documentsreporting.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.*;
import zw.ac.uz.emhare.documentsreporting.integration.*;
import zw.ac.uz.emhare.documentsreporting.upload.domain.model.*;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface DocumentsReportingIntegrationInboxRepository
        extends JpaRepository<DocumentsReportingIntegrationInbox, UUID> {
}
