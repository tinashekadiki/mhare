package zw.ac.uz.emhare.studentrecords.conversion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentConversionRequestRepository extends JpaRepository<StudentConversionRequest, UUID> {
    Optional<StudentConversionRequest> findBySourceOfferIdAndDeletedAtIsNull(UUID sourceOfferId);
    List<StudentConversionRequest> findAllByDeletedAtIsNullOrderByRequestedAtDesc();
}
