package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LookupValueRepository extends JpaRepository<LookupValue, UUID> {
    Optional<LookupValue> findByLookupSetAndCode(LookupSet lookupSet, String code);

    List<LookupValue> findByLookupSetIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(UUID lookupSetId);
}
