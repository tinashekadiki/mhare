package zw.ac.uz.emhare.finance.payment.infrastructure.persistence;

import zw.ac.uz.emhare.finance.payment.domain.model.ApplicationPaymentReference;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationPaymentReferenceRepository extends JpaRepository<ApplicationPaymentReference, UUID> {

    Optional<ApplicationPaymentReference> findBySourceApplicationIdAndDeletedAtIsNull(UUID sourceApplicationId);

    List<ApplicationPaymentReference> findBySourceApplicationIdInAndDeletedAtIsNull(Collection<UUID> sourceApplicationIds);
}
