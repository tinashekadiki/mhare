package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQualificationRequirementGroup;

/** @author Tinashe K */
public interface AdmissionQualificationRequirementGroupRepository
        extends JpaRepository<AdmissionQualificationRequirementGroup, UUID> {
    List<AdmissionQualificationRequirementGroup> findAllByRequirementSetIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID requirementSetId);
}
