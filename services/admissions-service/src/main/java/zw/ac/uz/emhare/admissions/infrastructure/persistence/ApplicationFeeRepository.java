package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicationFee;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationFeeRepository extends JpaRepository<ApplicationFee, UUID> {

    @Query("""
            select fee
            from ApplicationFee fee
            where fee.applicationType.id = :applicationTypeId
              and fee.applicantCategoryCode = :applicantCategoryCode
              and fee.active = true
              and fee.deletedAt is null
              and fee.effectiveFrom <= :effectiveDate
              and (fee.effectiveTo is null or fee.effectiveTo >= :effectiveDate)
            order by fee.effectiveFrom desc
            """)
    List<ApplicationFee> findEffectiveFees(
            @Param("applicationTypeId") UUID applicationTypeId,
            @Param("applicantCategoryCode") String applicantCategoryCode,
            @Param("effectiveDate") LocalDate effectiveDate);
}
