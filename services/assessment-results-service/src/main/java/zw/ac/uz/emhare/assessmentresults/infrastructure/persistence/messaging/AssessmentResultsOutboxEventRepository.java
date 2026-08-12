package zw.ac.uz.emhare.assessmentresults.infrastructure.persistence.messaging;

import zw.ac.uz.emhare.assessmentresults.infrastructure.messaging.model.AssessmentResultsOutboxEvent;

import zw.ac.uz.emhare.assessmentresults.assessment.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.assessmentresults.integration.*;
import zw.ac.uz.emhare.assessmentresults.progression.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.result.domain.model.*;
import zw.ac.uz.emhare.assessmentresults.roster.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** @author Tinashe K */
public interface AssessmentResultsOutboxEventRepository
        extends JpaRepository<AssessmentResultsOutboxEvent, UUID> {

    @Query(value = """
            SELECT * FROM integration_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY occurred_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 25
            """, nativeQuery = true)
    List<AssessmentResultsOutboxEvent> lockNextDispatchBatch(@Param("now") Instant now);
}
