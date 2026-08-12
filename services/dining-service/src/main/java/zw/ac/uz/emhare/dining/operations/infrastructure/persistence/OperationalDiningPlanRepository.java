package zw.ac.uz.emhare.dining.operations.infrastructure.persistence;

import zw.ac.uz.emhare.dining.setup.domain.model.DiningPlan;

import zw.ac.uz.emhare.dining.operations.*;
import zw.ac.uz.emhare.dining.operations.domain.model.*;
import zw.ac.uz.emhare.dining.setup.domain.model.*;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.dining.setup.*;

/** Spring Data persistence adapter. @author Tinashe K */
public interface OperationalDiningPlanRepository extends JpaRepository<DiningPlan,UUID>{}
