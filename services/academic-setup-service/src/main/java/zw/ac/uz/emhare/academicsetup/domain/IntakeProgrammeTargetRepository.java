package zw.ac.uz.emhare.academicsetup.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** @author Tinashe K */
public interface IntakeProgrammeTargetRepository extends JpaRepository<IntakeProgrammeTarget, UUID> {

    @Query("""
            select target
            from IntakeProgrammeTarget target
            join fetch target.programme programme
            join fetch programme.programmeType programmeType
            where target.intake.id = :intakeId
            order by programme.code
            """)
    List<IntakeProgrammeTarget> findAllByIntakeIdWithProgrammes(UUID intakeId);

    @Query("""
            select target
            from IntakeProgrammeTarget target
            join fetch target.programme programme
            join fetch programme.programmeType programmeType
            order by target.intake.id, programme.code
            """)
    List<IntakeProgrammeTarget> findAllWithProgrammes();
}
