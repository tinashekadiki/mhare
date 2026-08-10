package zw.ac.uz.emhare.admissions.application;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusEventRepository extends JpaRepository<ApplicationStatusEvent, UUID> {
}
