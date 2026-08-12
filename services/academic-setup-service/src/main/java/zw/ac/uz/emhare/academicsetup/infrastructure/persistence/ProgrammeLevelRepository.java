package zw.ac.uz.emhare.academicsetup.infrastructure.persistence;

import zw.ac.uz.emhare.academicsetup.domain.model.ProgrammeLevel;

import zw.ac.uz.emhare.academicsetup.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgrammeLevelRepository extends JpaRepository<ProgrammeLevel, UUID> {
    List<ProgrammeLevel> findAllByOrderBySortOrderAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsBySortOrder(int sortOrder);
}
