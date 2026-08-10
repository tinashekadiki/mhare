package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface IntakeRepository extends JpaRepository<Intake, UUID> {
    List<Intake> findAllByOrderByStartsOnDesc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    boolean existsByAcademicYearIdAndStatus(UUID academicYearId, CalendarStatus status);
    List<Intake> findAllByAcademicYearId(UUID academicYearId);
}
