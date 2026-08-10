package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {
    List<AcademicPeriod> findAllByOrderByStartDateDesc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    boolean existsByAcademicYearIdAndStatus(UUID academicYearId, CalendarStatus status);
    boolean existsByAcademicPeriodTypeId(UUID academicPeriodTypeId);
    List<AcademicPeriod> findAllByAcademicYearId(UUID academicYearId);
}
