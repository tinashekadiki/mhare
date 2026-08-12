package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeEntryOptionSelection;

/** @author Tinashe K */
public interface ApplicationProgrammeEntryOptionSelectionRepository
        extends JpaRepository<ApplicationProgrammeEntryOptionSelection, UUID> {
    List<ApplicationProgrammeEntryOptionSelection>
            findAllByProgrammeChoice_IdAndDeletedAtIsNullOrderByPreferenceRankAsc(UUID programmeChoiceId);
}
