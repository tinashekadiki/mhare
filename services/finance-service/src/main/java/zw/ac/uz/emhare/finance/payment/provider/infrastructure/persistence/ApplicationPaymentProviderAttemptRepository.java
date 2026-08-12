package zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence;

import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.ApplicationPaymentProviderAttempt;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationPaymentProviderAttemptRepository
        extends JpaRepository<ApplicationPaymentProviderAttempt, UUID> {
    Optional<ApplicationPaymentProviderAttempt> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ApplicationPaymentProviderAttempt>
            findFirstBySourceApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID sourceApplicationId);
}
