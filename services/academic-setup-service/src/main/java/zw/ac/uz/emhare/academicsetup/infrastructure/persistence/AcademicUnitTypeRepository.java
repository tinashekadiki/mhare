package zw.ac.uz.emhare.academicsetup.infrastructure.persistence;

import zw.ac.uz.emhare.academicsetup.domain.model.AcademicUnitType;

import zw.ac.uz.emhare.academicsetup.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicUnitTypeRepository extends JpaRepository<AcademicUnitType, UUID> {
    List<AcademicUnitType> findAllByOrderByLevelOrderAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    boolean existsByLevelOrder(int levelOrder);
}
