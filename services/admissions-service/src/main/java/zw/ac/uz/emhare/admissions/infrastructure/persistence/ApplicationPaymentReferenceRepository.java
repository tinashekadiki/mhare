package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicationPaymentReference;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationPaymentReferenceRepository extends JpaRepository<ApplicationPaymentReference, UUID> {

    Optional<ApplicationPaymentReference> findByApplicationIdAndDeletedAtIsNull(UUID applicationId);

    List<ApplicationPaymentReference> findByApplicationIdInAndDeletedAtIsNull(Collection<UUID> applicationIds);
}
