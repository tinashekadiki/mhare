package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, UUID> {
    Optional<Country> findByIso2Code(String iso2Code);

    List<Country> findByDeletedAtIsNullOrderByNameAsc();
}
