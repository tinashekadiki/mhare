package zw.ac.uz.emhare.academicsetup.infrastructure.persistence;

import zw.ac.uz.emhare.academicsetup.domain.model.AcademicModule;

import zw.ac.uz.emhare.academicsetup.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicModuleRepository extends JpaRepository<AcademicModule, UUID> {
    List<AcademicModule> findAllByOrderByCodeAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    boolean existsByOwningAcademicUnitId(UUID academicUnitId);
    boolean existsByOwningAcademicUnitAcademicUnitTypeId(UUID academicUnitTypeId);
}
