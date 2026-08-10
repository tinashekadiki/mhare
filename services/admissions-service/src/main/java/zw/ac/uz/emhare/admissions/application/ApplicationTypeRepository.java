package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationTypeRepository extends JpaRepository<ApplicationType, UUID> {

    List<ApplicationType> findAllByDeletedAtIsNullOrderByNameAsc();

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);
}
