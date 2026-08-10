package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AcademicPeriodTypeRepository extends JpaRepository<AcademicPeriodType, UUID> {
    List<AcademicPeriodType> findAllByOrderBySortOrderAsc();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsBySortOrder(int sortOrder);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
    boolean existsBySortOrderAndIdNot(int sortOrder, UUID id);
}
