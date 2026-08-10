package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface ApplicantQualificationResultRepository extends JpaRepository<ApplicantQualificationResult, UUID> {

    @Query("""
            select result
            from ApplicantQualificationResult result
            join fetch result.qualificationSitting sitting
            where sitting.application.id = :applicationId
              and result.deletedAt is null
              and sitting.deletedAt is null
            """)
    List<ApplicantQualificationResult> findAllForApplication(@Param("applicationId") UUID applicationId);

    List<ApplicantQualificationResult> findAllByQualificationSittingIdAndDeletedAtIsNullOrderBySubjectNameSnapshotAsc(UUID qualificationSittingId);
    @Query("""
            select result.subject.id
            from ApplicantQualificationResult result
            where result.qualificationSitting.id = :sittingId
              and result.subject is not null
              and result.deletedAt is null
            """)
    List<UUID> findActiveSubjectIdsByQualificationSittingId(@Param("sittingId") UUID sittingId);
    java.util.Optional<ApplicantQualificationResult> findByIdAndQualificationSittingIdAndDeletedAtIsNull(UUID id, UUID qualificationSittingId);
    long countByQualificationSittingIdAndDeletedAtIsNull(UUID qualificationSittingId);
}
