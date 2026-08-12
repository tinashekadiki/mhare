package zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence;

import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationWaitlistEntry;

import zw.ac.uz.emhare.accommodation.operations.*;
import zw.ac.uz.emhare.accommodation.operations.domain.model.*;
import zw.ac.uz.emhare.accommodation.setup.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoom;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoomType;

/** Spring Data persistence adapter. @author Tinashe K */
public interface AccommodationWaitlistRepository extends JpaRepository<AccommodationWaitlistEntry, UUID> {
    List<AccommodationWaitlistEntry> findAllByDeletedAtIsNullOrderByApplicationPeriodIdAscWaitlistPositionAsc();
    Optional<AccommodationWaitlistEntry> findByApplicationIdAndStatus(UUID applicationId, AccommodationWaitlistEntry.Status status);
    @Query(value = "select pg_advisory_xact_lock(hashtextextended(cast(:periodId as text), 0))", nativeQuery = true)
    Long acquirePeriodLock(@Param("periodId") UUID periodId);
    @Query("select coalesce(max(entry.waitlistPosition), 0) from AccommodationWaitlistEntry entry "
            + "where entry.applicationPeriod.id = :periodId and entry.status = zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationWaitlistEntry.Status.ACTIVE")
    int maximumActivePosition(@Param("periodId") UUID periodId);
}
