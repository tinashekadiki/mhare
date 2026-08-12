package zw.ac.uz.emhare.finance.collections.domain.model;

import zw.ac.uz.emhare.finance.collections.*;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Append-only reversal preserving the original reconciled payment and receipt. @author Tinashe K */
@Audited @Entity @Table(name="student_payment_reversals") @SQLRestriction("deleted_at IS NULL")
public class StudentPaymentReversal extends AuditableEntity {
    @Column(name="reversal_number",nullable=false,length=40) private String reversalNumber;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id") private StudentAccountPayment payment;
    @Column(name="reversed_by_user_id",nullable=false) private UUID reversedByUserId;
    @Column(name="reversed_at",nullable=false) private Instant reversedAt;
    @Column(name="reversal_reason",nullable=false,length=1000) private String reversalReason;
    protected StudentPaymentReversal() {}
    public StudentPaymentReversal(String number,StudentAccountPayment payment,UUID actor,Instant at,String reason){reversalNumber=required(number,"Reversal number");this.payment=Objects.requireNonNull(payment);reversedByUserId=Objects.requireNonNull(actor);reversedAt=Objects.requireNonNull(at);reversalReason=required(reason,"Reversal reason");}
    private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    public String getReversalNumber(){return reversalNumber;} public StudentAccountPayment getPayment(){return payment;} public UUID getReversedByUserId(){return reversedByUserId;} public Instant getReversedAt(){return reversedAt;}
}
