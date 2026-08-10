package zw.ac.uz.emhare.admissions.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantRepository extends JpaRepository<Applicant, UUID> {
    Optional<Applicant> findByUserId(UUID userId);
    Optional<Applicant> findByNationalIdNumberIgnoreCaseAndDeletedAtIsNull(String nationalIdNumber);
    Optional<Applicant> findByPassportNumberIgnoreCaseAndDeletedAtIsNull(String passportNumber);

    Optional<Applicant> findByIdAndDeletedAtIsNull(UUID applicantId);

    @Query(
            value = """
                    SELECT applicant
                    FROM Applicant applicant
                    WHERE applicant.deletedAt IS NULL
                      AND (:searchText = ''
                        OR LOWER(applicant.applicantNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.firstName) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.middleNames) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.lastName) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.primaryEmail) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.nationalIdNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.passportNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR EXISTS (
                            SELECT applicationForSearch.id
                            FROM Application applicationForSearch
                            WHERE applicationForSearch.applicant = applicant
                              AND applicationForSearch.deletedAt IS NULL
                              AND LOWER(applicationForSearch.applicationNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                              AND (:applicationStatus IS NULL OR applicationForSearch.status = :applicationStatus)))
                      AND (:applicantCategoryCode IS NULL OR applicant.applicantCategoryCode = :applicantCategoryCode)
                      AND (:applicationStatus IS NULL OR EXISTS (
                          SELECT applicationForStatus.id
                          FROM Application applicationForStatus
                          WHERE applicationForStatus.applicant = applicant
                            AND applicationForStatus.deletedAt IS NULL
                            AND applicationForStatus.status = :applicationStatus))
                    """,
            countQuery = """
                    SELECT COUNT(applicant.id)
                    FROM Applicant applicant
                    WHERE applicant.deletedAt IS NULL
                      AND (:searchText = ''
                        OR LOWER(applicant.applicantNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.firstName) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.middleNames) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.lastName) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.primaryEmail) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.nationalIdNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR LOWER(applicant.passportNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                        OR EXISTS (
                            SELECT applicationForSearch.id
                            FROM Application applicationForSearch
                            WHERE applicationForSearch.applicant = applicant
                              AND applicationForSearch.deletedAt IS NULL
                              AND LOWER(applicationForSearch.applicationNumber) LIKE LOWER(CONCAT('%', :searchText, '%'))
                              AND (:applicationStatus IS NULL OR applicationForSearch.status = :applicationStatus)))
                      AND (:applicantCategoryCode IS NULL OR applicant.applicantCategoryCode = :applicantCategoryCode)
                      AND (:applicationStatus IS NULL OR EXISTS (
                          SELECT applicationForStatus.id
                          FROM Application applicationForStatus
                          WHERE applicationForStatus.applicant = applicant
                            AND applicationForStatus.deletedAt IS NULL
                            AND applicationForStatus.status = :applicationStatus))
                    """)
    Page<Applicant> findRegisterPage(
            @Param("searchText") String searchText,
            @Param("applicantCategoryCode") String applicantCategoryCode,
            @Param("applicationStatus") ApplicationStatus applicationStatus,
            Pageable pageable);
}
