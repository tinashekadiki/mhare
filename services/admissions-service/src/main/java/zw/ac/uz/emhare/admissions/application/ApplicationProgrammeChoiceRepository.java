package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicationProgrammeChoiceRepository extends JpaRepository<ApplicationProgrammeChoice, UUID> {
    List<ApplicationProgrammeChoice> findAllByApplicationIdOrderByChoiceRankAsc(UUID applicationId);
    List<ApplicationProgrammeChoice> findAllByOwningAcademicUnitId(UUID owningAcademicUnitId);
}
