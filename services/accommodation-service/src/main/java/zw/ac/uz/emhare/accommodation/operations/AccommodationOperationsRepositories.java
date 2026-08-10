package zw.ac.uz.emhare.accommodation.operations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.accommodation.setup.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.AccommodationRoom;
import zw.ac.uz.emhare.accommodation.setup.AccommodationRoomType;

/** @author Tinashe K */
interface OperationalApplicationPeriodRepository extends JpaRepository<AccommodationApplicationPeriod, UUID> {}
interface OperationalRoomTypeRepository extends JpaRepository<AccommodationRoomType, UUID> {}
interface OperationalRoomRepository extends JpaRepository<AccommodationRoom, UUID> {}

interface AccommodationRateRepository extends JpaRepository<AccommodationRate, UUID> {
    List<AccommodationRate> findAllByDeletedAtIsNullOrderByEffectiveFromDesc();
    boolean existsByApplicationPeriodIdAndRoomTypeIdAndRateVersion(UUID applicationPeriodId, UUID roomTypeId, int rateVersion);
}

interface AccommodationApplicationRepository extends JpaRepository<AccommodationApplication, UUID> {
    List<AccommodationApplication> findAllByDeletedAtIsNullOrderBySubmittedAtDesc();
    @Query(value = "select nextval('accommodation_application_number_seq')", nativeQuery = true)
    long nextApplicationNumber();
}

interface AccommodationWaitlistRepository extends JpaRepository<AccommodationWaitlistEntry, UUID> {
    List<AccommodationWaitlistEntry> findAllByDeletedAtIsNullOrderByApplicationPeriodIdAscWaitlistPositionAsc();
    Optional<AccommodationWaitlistEntry> findByApplicationIdAndStatus(UUID applicationId, AccommodationWaitlistEntry.Status status);
    @Query(value = "select pg_advisory_xact_lock(hashtextextended(cast(:periodId as text), 0))", nativeQuery = true)
    Long acquirePeriodLock(@Param("periodId") UUID periodId);
    @Query("select coalesce(max(entry.waitlistPosition), 0) from AccommodationWaitlistEntry entry "
            + "where entry.applicationPeriod.id = :periodId and entry.status = zw.ac.uz.emhare.accommodation.operations.AccommodationWaitlistEntry.Status.ACTIVE")
    int maximumActivePosition(@Param("periodId") UUID periodId);
}

interface RoomAllocationRepository extends JpaRepository<RoomAllocation, UUID> {
    List<RoomAllocation> findAllByDeletedAtIsNullOrderByAllocatedAtDesc();
    @Query(value = "select nextval('accommodation_allocation_number_seq')", nativeQuery = true)
    long nextAllocationNumber();
}

interface RoomAllocationEventRepository extends JpaRepository<RoomAllocationEvent, UUID> {
    List<RoomAllocationEvent> findAllByDeletedAtIsNullOrderByOccurredAtDesc();
}
