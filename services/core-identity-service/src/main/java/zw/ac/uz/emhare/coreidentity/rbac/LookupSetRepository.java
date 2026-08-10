package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LookupSetRepository extends JpaRepository<LookupSet, UUID> {
    Optional<LookupSet> findByCode(String code);

    List<LookupSet> findByDeletedAtIsNullOrderByCodeAsc();
}
