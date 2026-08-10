package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AdmissionSubjectRepository extends JpaRepository<AdmissionSubject, UUID> {
    List<AdmissionSubject> findAllByLevelAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(SubjectLevel level);
    List<AdmissionSubject> findAllByLevelAndDeletedAtIsNullOrderByNameAsc(SubjectLevel level);
    Optional<AdmissionSubject> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByLevelAndCodeIgnoreCaseAndDeletedAtIsNull(SubjectLevel level, String code);
}
