package zw.ac.uz.emhare.coreidentity.rbac;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
    List<LoginEvent> findTop100ByDeletedAtIsNullOrderByOccurredAtDesc();
}
