package zw.ac.uz.emhare.accommodation.setup;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
interface AccommodationPremiseRepository extends JpaRepository<AccommodationPremise, UUID> {
    List<AccommodationPremise> findAllByDeletedAtIsNullOrderByCodeAsc();
}
interface AccommodationRoomTypeRepository extends JpaRepository<AccommodationRoomType, UUID> {
    List<AccommodationRoomType> findAllByDeletedAtIsNullOrderByCodeAsc();
}
interface ResidenceHallRepository extends JpaRepository<ResidenceHall, UUID> {
    List<ResidenceHall> findAllByDeletedAtIsNullOrderByCodeAsc();
}
interface AccommodationRoomRepository extends JpaRepository<AccommodationRoom, UUID> {
    List<AccommodationRoom> findAllByDeletedAtIsNullOrderByCodeAsc();
}
interface AccommodationApplicationPeriodRepository extends JpaRepository<AccommodationApplicationPeriod, UUID> {
    List<AccommodationApplicationPeriod> findAllByDeletedAtIsNullOrderByApplicationsOpenAtDescCodeAsc();
}
