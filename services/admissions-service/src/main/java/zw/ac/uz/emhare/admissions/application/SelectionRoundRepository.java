package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface SelectionRoundRepository extends JpaRepository<SelectionRound, UUID> {
    List<SelectionRound> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
}
