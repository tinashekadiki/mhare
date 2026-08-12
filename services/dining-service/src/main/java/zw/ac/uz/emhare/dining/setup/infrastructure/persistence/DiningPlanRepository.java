package zw.ac.uz.emhare.dining.setup.infrastructure.persistence;

import zw.ac.uz.emhare.dining.setup.domain.model.DiningPlan;

import zw.ac.uz.emhare.dining.operations.domain.model.*;
import zw.ac.uz.emhare.dining.setup.*;
import zw.ac.uz.emhare.dining.setup.domain.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence adapter. @author Tinashe K */
public interface DiningPlanRepository extends JpaRepository<DiningPlan, UUID> { List<DiningPlan> findAllByDeletedAtIsNullOrderByCodeAscPlanVersionDesc(); }
