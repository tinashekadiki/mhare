package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** @author Tinashe K */
public interface ApplicationProgrammeChoiceRepository extends JpaRepository<ApplicationProgrammeChoice, UUID> {
    List<ApplicationProgrammeChoice> findAllByApplicationIdOrderByChoiceRankAsc(UUID applicationId);
    List<ApplicationProgrammeChoice> findAllByOwningAcademicUnitId(UUID owningAcademicUnitId);

    @Query(value = """
            select count(*) from application_programme_entry_option_selections selection
            join application_programme_choices choice on choice.id = selection.programme_choice_id
            where choice.application_id = :applicationId
              and choice.deleted_at is null and selection.deleted_at is null
            """, nativeQuery = true)
    long countEntryOptionSelections(UUID applicationId);
}
