package zw.ac.uz.emhare.studentrecords.registration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface RegistrationStatusEventRepository extends JpaRepository<RegistrationStatusEvent, UUID> {
    List<RegistrationStatusEvent> findAllByRegistrationSessionIdOrderByChangedAtAsc(UUID registrationSessionId);
}
