package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ProgrammeTypeRepository extends JpaRepository<ProgrammeType, UUID> {
    List<ProgrammeType> findAllByOrderByNameAsc();
    boolean existsByCodeIgnoreCase(String code);
}
