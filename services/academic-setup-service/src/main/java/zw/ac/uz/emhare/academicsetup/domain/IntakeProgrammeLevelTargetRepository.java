package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** @author Tinashe K */
public interface IntakeProgrammeLevelTargetRepository extends JpaRepository<IntakeProgrammeLevelTarget, UUID> {

    @Query("""
            select target
            from IntakeProgrammeLevelTarget target
            join fetch target.programmeLevel programmeLevel
            order by target.intake.id, programmeLevel.sortOrder, programmeLevel.name
            """)
    List<IntakeProgrammeLevelTarget> findAllWithProgrammeLevels();

    @Query("""
            select target
            from IntakeProgrammeLevelTarget target
            join fetch target.programmeLevel programmeLevel
            where target.intake.id = :intakeId
            order by programmeLevel.sortOrder, programmeLevel.name
            """)
    List<IntakeProgrammeLevelTarget> findAllByIntakeIdWithProgrammeLevels(UUID intakeId);
}
