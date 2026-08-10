package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicUnitTypeRepository extends JpaRepository<AcademicUnitType, UUID> {
    List<AcademicUnitType> findAllByOrderByLevelOrderAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByLevelOrder(int levelOrder);
}
