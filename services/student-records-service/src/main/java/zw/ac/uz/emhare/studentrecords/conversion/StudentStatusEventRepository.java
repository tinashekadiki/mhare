package zw.ac.uz.emhare.studentrecords.conversion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentStatusEventRepository extends JpaRepository<StudentStatusEvent, UUID> {
}
