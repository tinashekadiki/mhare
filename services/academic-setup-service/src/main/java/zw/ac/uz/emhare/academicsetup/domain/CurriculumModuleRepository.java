package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface CurriculumModuleRepository extends JpaRepository<CurriculumModule, UUID> {
    List<CurriculumModule> findAllByProgrammeVersionIdOrderByPeriodNumberAscSortOrderAsc(UUID programmeVersionId);
    long countByProgrammeVersionId(UUID programmeVersionId);
    boolean existsByProgrammeVersionIdAndAcademicModuleId(UUID programmeVersionId, UUID academicModuleId);
    boolean existsByProgrammeVersionIdAndSortOrder(UUID programmeVersionId, int sortOrder);
    boolean existsByProgrammeVersionIdAndSortOrderAndIdNot(UUID programmeVersionId, int sortOrder, UUID curriculumModuleId);
}
