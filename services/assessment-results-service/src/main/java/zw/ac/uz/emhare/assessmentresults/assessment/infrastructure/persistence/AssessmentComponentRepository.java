package zw.ac.uz.emhare.assessmentresults.assessment.infrastructure.persistence;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentComponent;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentEnums;

import zw.ac.uz.emhare.assessmentresults.assessment.*;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.*;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.AssessmentEnums.*;

/** Spring Data persistence adapter. @author Tinashe K */
public interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent,UUID>{
    Optional<AssessmentComponent> findByIdAndDeletedAtIsNull(UUID id);
    List<AssessmentComponent> findAllByAssessmentSchemeIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID schemeId);
}
