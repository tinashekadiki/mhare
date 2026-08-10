package zw.ac.uz.emhare.examstimetabling.roster;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
interface ExamRegistrationImportRepository extends JpaRepository<ExamRegistrationImport,UUID> {
    Optional<ExamRegistrationImport> findByRegistrationSessionIdAndDeletedAtIsNull(UUID registrationSessionId);
    List<ExamRegistrationImport> findAllByAcademicPeriodIdAndDeletedAtIsNullOrderByStudentNumberAsc(UUID academicPeriodId);
}

interface ExamCandidateModuleRepository extends JpaRepository<ExamCandidateModule,UUID> {
    List<ExamCandidateModule> findAllByRegistrationImportAcademicPeriodIdAndEligibilityStatusAndDeletedAtIsNull(
            UUID academicPeriodId, ExamCandidateModule.EligibilityStatus eligibilityStatus);
}
