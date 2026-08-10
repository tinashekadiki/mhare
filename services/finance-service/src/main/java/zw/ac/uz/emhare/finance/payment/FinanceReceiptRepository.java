package zw.ac.uz.emhare.finance.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceReceiptRepository extends JpaRepository<FinanceReceipt, UUID> {
}
