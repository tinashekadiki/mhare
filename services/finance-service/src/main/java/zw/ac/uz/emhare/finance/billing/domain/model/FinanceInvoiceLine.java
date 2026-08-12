package zw.ac.uz.emhare.finance.billing.domain.model;

import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRule;

import zw.ac.uz.emhare.finance.billing.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.Audited;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;
import zw.ac.uz.emhare.finance.catalogue.*;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;

/** Exact immutable copy of approved charge and posting-account evidence. @author Tinashe K */
@Audited @Entity @Table(name="finance_invoice_lines") @SQLRestriction("deleted_at IS NULL")
public class FinanceInvoiceLine extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id") private FinanceInvoice invoice;
    @Column(name="line_number",nullable=false) private int lineNumber; @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="billing_event_id") private FinanceBillingEvent billingEvent;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fee_catalogue_id") private FinanceFeeCatalogue feeCatalogue; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fee_rule_id") private FinanceFeeRule feeRule;
    @Column(name="fee_code",nullable=false,length=50) private String feeCode; @Column(nullable=false,length=500) private String description; @Column(nullable=false,precision=12,scale=4) private BigDecimal quantity;
    @Column(name="transaction_currency_code",nullable=false,length=3) private String transactionCurrencyCode; @Column(name="transaction_unit_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionUnitAmount; @Column(name="gross_transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal grossTransactionAmount; @Column(name="transaction_discount_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionDiscountAmount; @Column(name="transaction_amount",nullable=false,precision=16,scale=2) private BigDecimal transactionAmount;
    @Column(name="base_currency_code",nullable=false,length=3) private String baseCurrencyCode; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="exchange_rate_id") private ExchangeRate exchangeRate; @Column(name="base_unit_amount",nullable=false,precision=16,scale=2) private BigDecimal baseUnitAmount; @Column(name="gross_base_amount",nullable=false,precision=16,scale=2) private BigDecimal grossBaseAmount; @Column(name="base_discount_amount",nullable=false,precision=16,scale=2) private BigDecimal baseDiscountAmount; @Column(name="base_amount",nullable=false,precision=16,scale=2) private BigDecimal baseAmount;
    @Column(name="discount_rule_id") private java.util.UUID discountRuleId; @Column(name="discount_rule_code",length=50) private String discountRuleCode; @Column(name="discount_percentage",precision=7,scale=4) private BigDecimal discountPercentage;
    @Column(name="receivable_account_code",nullable=false,length=50) private String receivableAccountCode; @Column(name="revenue_account_code",nullable=false,length=50) private String revenueAccountCode; @Column(name="tax_code",length=30) private String taxCode;
    protected FinanceInvoiceLine() {}
    public FinanceInvoiceLine(FinanceInvoice invoice,int lineNumber,FinanceBillingEvent event){this.invoice=invoice;this.lineNumber=lineNumber;billingEvent=event;feeCatalogue=event.getFeeCatalogue();feeRule=event.getFeeRule();feeCode=feeCatalogue.getCode();description=event.getDescription();quantity=event.getQuantity();transactionCurrencyCode=event.getTransactionCurrencyCode();transactionUnitAmount=event.getTransactionUnitAmount();grossTransactionAmount=event.getGrossTransactionAmount();transactionDiscountAmount=event.getTransactionDiscountAmount();transactionAmount=event.getTransactionAmount();baseCurrencyCode=event.getBaseCurrencyCode();exchangeRate=event.getExchangeRate();baseUnitAmount=event.getBaseUnitAmount();grossBaseAmount=event.getGrossBaseAmount();baseDiscountAmount=event.getBaseDiscountAmount();baseAmount=event.getBaseAmount();discountRuleId=event.getDiscountRuleId();discountRuleCode=event.getDiscountRuleCode();discountPercentage=event.getDiscountPercentage();receivableAccountCode=feeCatalogue.getReceivableAccountCode();revenueAccountCode=feeCatalogue.getRevenueAccountCode();taxCode=feeCatalogue.getTaxCode();}
    public int getLineNumber(){return lineNumber;} public FinanceBillingEvent getBillingEvent(){return billingEvent;} public String getFeeCode(){return feeCode;} public String getDescription(){return description;} public BigDecimal getQuantity(){return quantity;}
    public BigDecimal getGrossTransactionAmount(){return grossTransactionAmount;} public BigDecimal getTransactionDiscountAmount(){return transactionDiscountAmount;} public BigDecimal getTransactionAmount(){return transactionAmount;} public BigDecimal getGrossBaseAmount(){return grossBaseAmount;} public BigDecimal getBaseDiscountAmount(){return baseDiscountAmount;} public BigDecimal getBaseAmount(){return baseAmount;} public java.util.UUID getDiscountRuleId(){return discountRuleId;} public String getDiscountRuleCode(){return discountRuleCode;} public BigDecimal getDiscountPercentage(){return discountPercentage;} public String getReceivableAccountCode(){return receivableAccountCode;} public String getRevenueAccountCode(){return revenueAccountCode;}
    public FinanceInvoice getInvoice(){return invoice;}
}
