package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionSubjectRequirement;

/** @author Tinashe K */
public interface AdmissionSubjectRequirementRepository extends JpaRepository<AdmissionSubjectRequirement, UUID> {
    List<AdmissionSubjectRequirement> findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID requirementSetId);
}
