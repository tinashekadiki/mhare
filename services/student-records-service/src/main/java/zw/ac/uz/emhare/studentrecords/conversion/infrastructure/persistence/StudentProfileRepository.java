package zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentProfile;

import zw.ac.uz.emhare.studentrecords.conversion.*;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findBySourceOfferIdAndDeletedAtIsNull(UUID sourceOfferId);
    Optional<StudentProfile> findByIdAndDeletedAtIsNull(UUID id);
    Optional<StudentProfile> findByUserIdAndDeletedAtIsNull(UUID userId);
}
