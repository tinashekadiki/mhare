package zw.ac.uz.emhare.finance.payment.infrastructure.persistence;

import zw.ac.uz.emhare.finance.payment.domain.model.FinanceReceipt;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceReceiptRepository extends JpaRepository<FinanceReceipt, UUID> {
}
