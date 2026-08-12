package zw.ac.uz.emhare.coreidentity.rbac.infrastructure.persistence;

import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupSet;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.LookupValue;

import zw.ac.uz.emhare.coreidentity.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.coreidentity.provisioning.domain.model.*;
import zw.ac.uz.emhare.coreidentity.rbac.*;
import zw.ac.uz.emhare.coreidentity.rbac.domain.model.*;
import zw.ac.uz.emhare.coreidentity.workflow.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LookupValueRepository extends JpaRepository<LookupValue, UUID> {
    Optional<LookupValue> findByLookupSetAndCode(LookupSet lookupSet, String code);

    List<LookupValue> findByLookupSetIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(UUID lookupSetId);
}
