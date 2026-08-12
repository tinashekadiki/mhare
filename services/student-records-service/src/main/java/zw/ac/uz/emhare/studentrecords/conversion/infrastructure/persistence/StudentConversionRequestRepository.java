package zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentConversionRequest;

import zw.ac.uz.emhare.studentrecords.conversion.*;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentConversionRequestRepository extends JpaRepository<StudentConversionRequest, UUID> {
    Optional<StudentConversionRequest> findBySourceOfferIdAndDeletedAtIsNull(UUID sourceOfferId);
    List<StudentConversionRequest> findAllByDeletedAtIsNullOrderByRequestedAtDesc();
}
