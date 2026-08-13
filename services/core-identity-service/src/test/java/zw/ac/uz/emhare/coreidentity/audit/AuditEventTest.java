package zw.ac.uz.emhare.coreidentity.audit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.coreidentity.audit.domain.model.AuditEvent;

/** Audit-event invariant regressions. @author Tinashe K */
class AuditEventTest {

    @Test
    void constructor_shouldRejectNullAndBlankRequiredText() {
        assertThatThrownBy(() -> new AuditEvent(UUID.randomUUID(), null, "USER", UUID.randomUUID(), "Changed.", null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEvent(UUID.randomUUID(), "UPDATE", " ", UUID.randomUUID(), "Changed.", null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
