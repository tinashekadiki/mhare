package zw.ac.uz.emhare.finance.collections.infrastructure.persistence;

import zw.ac.uz.emhare.finance.collections.domain.model.StudentPaymentAllocation;

import zw.ac.uz.emhare.finance.billing.domain.model.*;
import zw.ac.uz.emhare.finance.catalogue.domain.model.*;
import zw.ac.uz.emhare.finance.collections.*;
import zw.ac.uz.emhare.finance.collections.domain.model.*;
import zw.ac.uz.emhare.finance.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.finance.payment.domain.model.*;
import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.*;
import zw.ac.uz.emhare.finance.student.domain.model.*;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Spring Data persistence adapter. @author Tinashe K */
public interface StudentPaymentAllocationRepository extends JpaRepository<StudentPaymentAllocation,UUID> {
    List<StudentPaymentAllocation> findAllByDeletedAtIsNullOrderByAllocatedAtDescAllocationNumberDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select allocation from StudentPaymentAllocation allocation where allocation.id=:id and allocation.deletedAt is null") Optional<StudentPaymentAllocation> findLockedById(@Param("id") UUID id);
}
