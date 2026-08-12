package zw.ac.uz.emhare.dining.operations.domain.model;

import zw.ac.uz.emhare.dining.setup.domain.model.DiningHall;
import zw.ac.uz.emhare.dining.setup.domain.model.MealOption;

import zw.ac.uz.emhare.dining.operations.*;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.dining.setup.*;

/** @author Tinashe K */
@Audited @Entity @Table(name="meal_service_sessions") @SQLRestriction("deleted_at IS NULL")
public class MealServiceSession extends AuditableEntity {
    public enum Status { PLANNED, OPEN, CLOSED, RECONCILED, CANCELLED }
    @Column(name="session_number",nullable=false,length=60) private String sessionNumber;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="dining_hall_id") private DiningHall diningHall;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="meal_option_id") private MealOption mealOption;
    @Column(name="service_date",nullable=false) private LocalDate serviceDate;
    @Column(name="scheduled_opens_at",nullable=false) private Instant scheduledOpensAt; @Column(name="scheduled_closes_at",nullable=false) private Instant scheduledClosesAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(name="prepared_by_user_id",nullable=false) private UUID preparedByUserId; @Column(name="opened_by_user_id") private UUID openedByUserId; @Column(name="opened_at") private Instant openedAt;
    @Column(name="closed_by_user_id") private UUID closedByUserId; @Column(name="closed_at") private Instant closedAt; @Column(name="reconciled_by_user_id") private UUID reconciledByUserId; @Column(name="reconciled_at") private Instant reconciledAt;
    @Column(name="reconciliation_reason",length=1000) private String reconciliationReason; @Column(name="expected_servings") private Integer expectedServings; @Column(name="counted_servings") private Integer countedServings;
    protected MealServiceSession() {}
    public MealServiceSession(String number,DiningHall hall,MealOption option,LocalDate serviceDate,Instant opens,Instant closes,Integer expected,UUID preparer){
        if(hall==null||!hall.isActive()||option==null||!option.isActive()||serviceDate==null||opens==null||closes==null||preparer==null)throw new IllegalArgumentException("Active hall, meal option, service date, schedule, and preparing operator are required.");
        if(!closes.isAfter(opens)||expected!=null&&expected<0)throw new IllegalArgumentException("Meal session schedule or expected servings is invalid.");
        sessionNumber=DiningOperationValues.code(number,"Session number");diningHall=hall;mealOption=option;this.serviceDate=serviceDate;scheduledOpensAt=opens;scheduledClosesAt=closes;expectedServings=expected;preparedByUserId=preparer;status=Status.PLANNED;
    }
    public void open(UUID actor,Instant at,long expected){DiningOperationValues.version(getVersion(),expected,"Meal service session");if(status!=Status.PLANNED)throw new IllegalStateException("Only a planned meal session can open.");if(actor==null||actor.equals(preparedByUserId))throw new IllegalArgumentException("A different authorised operator must open the meal session.");openedByUserId=actor;openedAt=at;status=Status.OPEN;}
    public void close(UUID actor,Instant at,long expected){DiningOperationValues.version(getVersion(),expected,"Meal service session");if(status!=Status.OPEN)throw new IllegalStateException("Only an open meal session can close.");closedByUserId=actor;closedAt=at;status=Status.CLOSED;}
    public void reconcile(UUID actor,int counted,String reason,Instant at,long expected){DiningOperationValues.version(getVersion(),expected,"Meal service session");if(status!=Status.CLOSED)throw new IllegalStateException("Only a closed meal session can be reconciled.");if(actor==null||actor.equals(openedByUserId))throw new IllegalArgumentException("A different authorised operator must reconcile the meal session.");if(counted<0)throw new IllegalArgumentException("Counted servings cannot be negative.");reconciledByUserId=actor;reconciledAt=at;countedServings=counted;reconciliationReason=DiningOperationValues.required(reason,"Reconciliation reason");status=Status.RECONCILED;}
    public String getSessionNumber(){return sessionNumber;} public DiningHall getDiningHall(){return diningHall;} public MealOption getMealOption(){return mealOption;} public LocalDate getServiceDate(){return serviceDate;} public Instant getScheduledOpensAt(){return scheduledOpensAt;} public Instant getScheduledClosesAt(){return scheduledClosesAt;} public Status getStatus(){return status;} public UUID getPreparedByUserId(){return preparedByUserId;} public UUID getOpenedByUserId(){return openedByUserId;} public Instant getOpenedAt(){return openedAt;} public UUID getClosedByUserId(){return closedByUserId;} public Instant getClosedAt(){return closedAt;} public UUID getReconciledByUserId(){return reconciledByUserId;} public Instant getReconciledAt(){return reconciledAt;} public String getReconciliationReason(){return reconciliationReason;} public Integer getExpectedServings(){return expectedServings;} public Integer getCountedServings(){return countedServings;}
}
