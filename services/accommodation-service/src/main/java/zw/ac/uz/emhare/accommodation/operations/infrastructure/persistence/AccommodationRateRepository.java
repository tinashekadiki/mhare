package zw.ac.uz.emhare.accommodation.operations.infrastructure.persistence;

import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationRate;

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
public interface AccommodationRateRepository extends JpaRepository<AccommodationRate, UUID> {
    List<AccommodationRate> findAllByDeletedAtIsNullOrderByEffectiveFromDesc();
    boolean existsByApplicationPeriodIdAndRoomTypeIdAndRateVersion(UUID applicationPeriodId, UUID roomTypeId, int rateVersion);
}
