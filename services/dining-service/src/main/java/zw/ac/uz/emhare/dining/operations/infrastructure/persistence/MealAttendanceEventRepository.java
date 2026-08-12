package zw.ac.uz.emhare.dining.operations.infrastructure.persistence;

import zw.ac.uz.emhare.dining.operations.domain.model.MealAttendanceEvent;
import zw.ac.uz.emhare.dining.operations.domain.model.MealAttendanceReversal;

import zw.ac.uz.emhare.dining.operations.*;
import zw.ac.uz.emhare.dining.operations.domain.model.*;
import zw.ac.uz.emhare.dining.setup.domain.model.*;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import zw.ac.uz.emhare.dining.setup.*;

/** Spring Data persistence adapter. @author Tinashe K */
public interface MealAttendanceEventRepository extends JpaRepository<MealAttendanceEvent,UUID>{
    interface AttendanceStatisticProjection {
        String getDimension(); String getGroupCode(); long getAdmitted(); long getDenied(); long getReversed(); long getNetAdmitted();
    }
    List<MealAttendanceEvent> findAllByDeletedAtIsNullOrderByCapturedAtDesc();
    Optional<MealAttendanceEvent> findByIdempotencyKey(String key);
    @Query(value="select nextval('meal_attendance_event_number_seq')",nativeQuery=true) long nextNumber();
    @Query("select count(event) from MealAttendanceEvent event where event.session.id=:sessionId and event.outcome=zw.ac.uz.emhare.dining.operations.domain.model.MealAttendanceEvent.Outcome.ADMITTED and not exists (select reversal.id from MealAttendanceReversal reversal where reversal.event=event)")
    long netAdmitted(@Param("sessionId") UUID sessionId);
    @Query(value="""
        WITH attendance AS (
          SELECT e.id,e.outcome,e.student_dining_assignment_id,s.dining_hall_id,s.meal_option_id,
                 a.academic_period_code,a.programme_code,a.student_group_code,
                 CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS reversed
          FROM meal_attendance_events e
          JOIN meal_service_sessions s ON s.id=e.meal_service_session_id AND s.deleted_at IS NULL
          LEFT JOIN student_dining_assignments a ON a.id=e.student_dining_assignment_id AND a.deleted_at IS NULL
          LEFT JOIN meal_attendance_reversals r ON r.meal_attendance_event_id=e.id AND r.deleted_at IS NULL
          WHERE e.deleted_at IS NULL
        ), grouped AS (
          SELECT 'DINING_HALL' AS dimension,h.code AS group_code,a.outcome,a.reversed
          FROM attendance a JOIN dining_halls h ON h.id=a.dining_hall_id
          UNION ALL SELECT 'MEAL_OPTION',m.code,a.outcome,a.reversed FROM attendance a JOIN meal_options m ON m.id=a.meal_option_id
          UNION ALL SELECT 'ACADEMIC_PERIOD',coalesce(a.academic_period_code,'UNASSIGNED'),a.outcome,a.reversed FROM attendance a
          UNION ALL SELECT 'PROGRAMME',coalesce(a.programme_code,'UNASSIGNED'),a.outcome,a.reversed FROM attendance a
          UNION ALL SELECT 'STUDENT_GROUP',coalesce(a.student_group_code,'UNASSIGNED'),a.outcome,a.reversed FROM attendance a
        )
        SELECT dimension AS "dimension",group_code AS "groupCode",
          count(*) FILTER (WHERE outcome='ADMITTED') AS "admitted",
          count(*) FILTER (WHERE outcome='DENIED') AS "denied",
          sum(reversed) AS "reversed",
          count(*) FILTER (WHERE outcome='ADMITTED')
            - count(*) FILTER (WHERE outcome='ADMITTED' AND reversed=1) AS "netAdmitted"
        FROM grouped GROUP BY dimension,group_code ORDER BY dimension,group_code
        """,nativeQuery=true)
    List<AttendanceStatisticProjection> statistics();
}
