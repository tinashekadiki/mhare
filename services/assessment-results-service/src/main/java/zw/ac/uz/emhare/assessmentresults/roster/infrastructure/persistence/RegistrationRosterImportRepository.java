package zw.ac.uz.emhare.assessmentresults.roster.infrastructure.persistence;

import zw.ac.uz.emhare.assessmentresults.roster.domain.model.RegistrationRosterImport;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.roster.*;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface RegistrationRosterImportRepository extends JpaRepository<RegistrationRosterImport, UUID> {
    Optional<RegistrationRosterImport> findByRegistrationSessionIdAndDeletedAtIsNull(UUID registrationSessionId);
}
