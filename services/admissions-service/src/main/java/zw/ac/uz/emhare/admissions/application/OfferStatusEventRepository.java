package zw.ac.uz.emhare.admissions.application;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface OfferStatusEventRepository extends JpaRepository<OfferStatusEvent, UUID> {
}
