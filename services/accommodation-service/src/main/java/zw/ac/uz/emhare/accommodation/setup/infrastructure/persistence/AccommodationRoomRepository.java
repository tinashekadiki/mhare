package zw.ac.uz.emhare.accommodation.setup.infrastructure.persistence;

import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoom;

import zw.ac.uz.emhare.accommodation.operations.domain.model.*;
import zw.ac.uz.emhare.accommodation.setup.*;
import zw.ac.uz.emhare.accommodation.setup.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence adapter. @author Tinashe K */
public interface AccommodationRoomRepository extends JpaRepository<AccommodationRoom, UUID> {
    List<AccommodationRoom> findAllByDeletedAtIsNullOrderByCodeAsc();
}
