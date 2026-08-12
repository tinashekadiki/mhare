package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQuota;

/** @author Tinashe K */
public interface AdmissionQuotaRepository extends JpaRepository<AdmissionQuota, UUID> {
    List<AdmissionQuota> findAllByIntakeIdAndDeletedAtIsNullOrderByProgrammeCodeAscQuotaTypeCodeAsc(UUID intakeId);
}
