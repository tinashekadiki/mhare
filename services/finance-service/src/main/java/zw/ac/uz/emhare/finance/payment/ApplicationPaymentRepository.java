package zw.ac.uz.emhare.finance.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationPaymentRepository extends JpaRepository<ApplicationPayment, UUID> {

    Optional<ApplicationPayment> findByProviderCodeAndProviderTransactionReference(
            String providerCode,
            String providerTransactionReference);
}
