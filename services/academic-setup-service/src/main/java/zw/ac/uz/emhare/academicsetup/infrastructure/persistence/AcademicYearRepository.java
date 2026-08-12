package zw.ac.uz.emhare.academicsetup.infrastructure.persistence;

import zw.ac.uz.emhare.academicsetup.domain.model.AcademicYear;

import zw.ac.uz.emhare.academicsetup.domain.model.*;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {
    List<AcademicYear> findAllByOrderByStartDateDesc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate endDate, LocalDate startDate);
    boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(LocalDate endDate, LocalDate startDate, UUID id);
}
