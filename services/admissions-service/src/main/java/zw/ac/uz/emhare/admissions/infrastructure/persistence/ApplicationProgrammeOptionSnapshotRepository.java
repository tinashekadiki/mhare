package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeOptionSnapshot;

/** @author Tinashe K */
public interface ApplicationProgrammeOptionSnapshotRepository extends JpaRepository<ApplicationProgrammeOptionSnapshot, UUID> {
    List<ApplicationProgrammeOptionSnapshot> findAllByApplicationIdAndDeletedAtIsNullOrderByProgrammeCodeAsc(UUID applicationId);
    Optional<ApplicationProgrammeOptionSnapshot> findByApplicationIdAndProgrammeIdAndDeletedAtIsNull(
            UUID applicationId, UUID programmeId);
}
