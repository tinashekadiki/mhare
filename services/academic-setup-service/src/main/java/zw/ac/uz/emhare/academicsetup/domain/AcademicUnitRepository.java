package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicUnitRepository extends JpaRepository<AcademicUnit, UUID> {
    List<AcademicUnit> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByParentId(UUID parentId);
}
