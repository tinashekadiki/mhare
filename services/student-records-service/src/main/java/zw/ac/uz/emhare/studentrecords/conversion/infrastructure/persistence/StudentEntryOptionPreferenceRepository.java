package zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentEntryOptionPreference;

/** @author Tinashe K */
public interface StudentEntryOptionPreferenceRepository extends JpaRepository<StudentEntryOptionPreference, UUID> { }
