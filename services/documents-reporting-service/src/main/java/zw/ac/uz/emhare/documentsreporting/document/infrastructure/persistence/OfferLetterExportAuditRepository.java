package zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.OfferLetterExportAudit;

/** @author Tinashe K */
public interface OfferLetterExportAuditRepository extends JpaRepository<OfferLetterExportAudit,UUID> { }
