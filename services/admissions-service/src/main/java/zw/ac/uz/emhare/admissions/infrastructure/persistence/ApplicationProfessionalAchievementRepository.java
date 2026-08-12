package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProfessionalAchievement;

/** @author Tinashe K */
public interface ApplicationProfessionalAchievementRepository extends JpaRepository<ApplicationProfessionalAchievement, UUID> {
    List<ApplicationProfessionalAchievement> findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID applicationId);
}
