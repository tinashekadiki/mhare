package zw.ac.uz.emhare.academicsetup.infrastructure.persistence;

import zw.ac.uz.emhare.academicsetup.domain.model.Programme;

import zw.ac.uz.emhare.academicsetup.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgrammeRepository extends JpaRepository<Programme, UUID> {
    List<Programme> findAllByOrderByCodeAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByOwningAcademicUnitId(UUID academicUnitId);
}
