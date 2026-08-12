package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.GradingScale;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {

    @Query("""
            select scale
            from GradingScale scale
            where scale.level = :level
              and scale.effectiveFrom <= :date
              and (scale.effectiveTo is null or scale.effectiveTo >= :date)
              and scale.deletedAt is null
            """)
    Optional<GradingScale> findApplicableScale(@Param("level") QualificationLevel level, @Param("date") LocalDate date);
}
