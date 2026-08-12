package zw.ac.uz.emhare.finance.collections.domain.model;

import zw.ac.uz.emhare.finance.collections.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.student.domain.model.StudentFinanceAccount;

/** Append-only resolution of a reconciled payment held in suspense. @author Tinashe K */
@Audited @Entity @Table(name="student_payment_suspense_resolutions") @SQLRestriction("deleted_at IS NULL")
public class StudentPaymentSuspenseResolution extends AuditableEntity {
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id") private StudentAccountPayment payment;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="student_finance_account_id") private StudentFinanceAccount studentFinanceAccount;
    @Column(name="resolved_by_user_id",nullable=false) private UUID resolvedByUserId;
    @Column(name="resolved_at",nullable=false) private Instant resolvedAt;
    @Column(name="resolution_reason",nullable=false,length=1000) private String resolutionReason;
    protected StudentPaymentSuspenseResolution() {}
    public StudentPaymentSuspenseResolution(StudentAccountPayment payment,StudentFinanceAccount account,UUID actor,Instant at,String reason){this.payment=Objects.requireNonNull(payment);studentFinanceAccount=Objects.requireNonNull(account);resolvedByUserId=Objects.requireNonNull(actor);resolvedAt=Objects.requireNonNull(at);if(reason==null||reason.isBlank())throw new IllegalArgumentException("Suspense-resolution reason is required.");resolutionReason=reason.trim();}
    public StudentAccountPayment getPayment(){return payment;} public StudentFinanceAccount getStudentFinanceAccount(){return studentFinanceAccount;} public UUID getResolvedByUserId(){return resolvedByUserId;} public Instant getResolvedAt(){return resolvedAt;}
}
