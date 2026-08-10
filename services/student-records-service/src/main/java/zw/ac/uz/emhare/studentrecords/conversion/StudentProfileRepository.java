package zw.ac.uz.emhare.studentrecords.conversion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findBySourceOfferIdAndDeletedAtIsNull(UUID sourceOfferId);
    Optional<StudentProfile> findByIdAndDeletedAtIsNull(UUID id);
    Optional<StudentProfile> findByUserIdAndDeletedAtIsNull(UUID userId);
}
