package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQualificationRequirementItem;

/** @author Tinashe K */
public interface AdmissionQualificationRequirementItemRepository
        extends JpaRepository<AdmissionQualificationRequirementItem, UUID> {
    List<AdmissionQualificationRequirementItem> findAllByRequirementGroupIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID requirementGroupId);
}
