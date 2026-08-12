package zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence;

import zw.ac.uz.emhare.accommodation.operations.domain.model.RoomAllocation;

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
public interface RoomAllocationRepository extends JpaRepository<RoomAllocation, UUID> {
    List<RoomAllocation> findAllByDeletedAtIsNullOrderByAllocatedAtDesc();
    @Query(value = "select nextval('accommodation_allocation_number_seq')", nativeQuery = true)
    long nextAllocationNumber();
}
