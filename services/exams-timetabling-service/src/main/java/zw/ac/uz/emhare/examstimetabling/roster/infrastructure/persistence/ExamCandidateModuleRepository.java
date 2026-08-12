package zw.ac.uz.emhare.examstimetabling.roster.infrastructure.persistence;

import zw.ac.uz.emhare.examstimetabling.roster.domain.model.ExamCandidateModule;

import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.roster.*;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence adapter. @author Tinashe K */
public interface ExamCandidateModuleRepository extends JpaRepository<ExamCandidateModule,UUID> {
    List<ExamCandidateModule> findAllByRegistrationImportAcademicPeriodIdAndEligibilityStatusAndDeletedAtIsNull(
            UUID academicPeriodId, ExamCandidateModule.EligibilityStatus eligibilityStatus);
}
