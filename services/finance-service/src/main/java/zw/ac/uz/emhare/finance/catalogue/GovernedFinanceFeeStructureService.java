package zw.ac.uz.emhare.finance.catalogue;

import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeCatalogue;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRule;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeRuleScope;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructure;
import zw.ac.uz.emhare.finance.catalogue.domain.model.FinanceFeeStructureAttachment;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeCatalogueRepository;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeRuleRepository;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeRuleScopeRepository;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeStructureAttachmentRepository;
import zw.ac.uz.emhare.finance.catalogue.infrastructure.persistence.FinanceFeeStructureRepository;
import zw.ac.uz.emhare.finance.payment.infrastructure.persistence.ExchangeRateRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.ApplicationFeePricing;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.AttachmentInput;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.CreateStructure;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.LineInput;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.ResolveStructure;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureAttachmentSummary;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureDecision;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureLineSummary;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureRegister;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.StructureSummary;
import zw.ac.uz.emhare.finance.payment.domain.model.ExchangeRate;
import zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels;

/** Creates and resolves complete fee schedules with whole-structure precedence. @author Tinashe K */
@Service
public class GovernedFinanceFeeStructureService {
    private final FinanceFeeStructureRepository structureRepository;
    private final FinanceFeeCatalogueRepository catalogueRepository;
    private final FinanceFeeRuleRepository ruleRepository;
    private final FinanceFeeRuleScopeRepository scopeRepository;
    private final FinanceFeeStructureAttachmentRepository attachmentRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final Clock clock;

    public GovernedFinanceFeeStructureService(FinanceFeeStructureRepository structureRepository,
            FinanceFeeCatalogueRepository catalogueRepository, FinanceFeeRuleRepository ruleRepository,
            FinanceFeeRuleScopeRepository scopeRepository, FinanceFeeStructureAttachmentRepository attachmentRepository,
            ExchangeRateRepository exchangeRateRepository, Clock clock) {
        this.structureRepository = structureRepository;
        this.catalogueRepository = catalogueRepository;
        this.ruleRepository = ruleRepository;
        this.scopeRepository = scopeRepository;
        this.attachmentRepository = attachmentRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StructureRegister register() {
        return new StructureRegister(structureRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::view).toList());
    }

    public ApplicationFeePricing pricing(UUID structureId) {
        FinanceFeeStructure structure = structureRepository.findById(structureId)
                .orElseThrow(() -> new IllegalArgumentException("Fee structure not found."));
        BigDecimal total = ruleRepository
                .findAllByFeeStructureIdAndDeletedAtIsNullOrderByStructureLineNumberAsc(structure.getId()).stream()
                .map(FinanceFeeRule::getTransactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ApplicationFeePricing(
                structure.getId(), structure.getCode(), structure.getName(), structure.getStatus(),
                structure.getTransactionCurrencyCode(), total);
    }

    @Transactional
    public StructureSummary create(CreateStructure command, UUID actor) {
        structureRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(command.code()).ifPresent(existing -> {
            throw new IllegalStateException("Fee structure code already exists.");
        });
        validateLineUniqueness(command.lines());
        BigDecimal structureTotal = command.lines().stream()
                .map(LineInput::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AttachmentInput> attachments = command.attachments() == null ? List.of() : command.attachments();
        if (!attachments.isEmpty()) {
            throw new IllegalArgumentException("Configure attachment discounts in the standalone student-discount register.");
        }
        FinanceFeeStructure structure = structureRepository.saveAndFlush(new FinanceFeeStructure(
                command.code(), command.name(), command.description(), command.feeContext(), command.scopeType(),
                command.scopeReferenceId(), command.scopeReferenceCode(), command.scopeReferenceName(),
                command.programmeLevelId(), command.programmeLevelCode(), command.programmeLevelName(),
                command.academicPeriodId(), command.academicPeriodCode(), command.academicPeriodName(),
                command.programmePeriodNumber(), command.applicantCategoryCode(), command.transactionCurrencyCode(),
                command.effectiveFrom(), command.effectiveUntil(), actor));

        RatedPrice ratedPrice = rate(command.transactionCurrencyCode(), command.effectiveFrom());
        int lineNumber = 1;
        for (LineInput line : command.lines()) {
            FinanceFeeCatalogue catalogue = resolveCatalogue(line, actor);
            int ruleVersion = ruleRepository.findFirstByFeeCatalogueIdAndDeletedAtIsNullOrderByRuleVersionDesc(catalogue.getId())
                    .map(existing -> existing.getRuleVersion() + 1).orElse(1);
            BigDecimal baseAmount = ratedPrice.exchangeRate() == null
                    ? ("USD".equalsIgnoreCase(command.transactionCurrencyCode()) ? line.amount() : null)
                    : line.amount().multiply(ratedPrice.exchangeRate().getRateToBase()).setScale(2, RoundingMode.HALF_UP);
            FinanceFeeRule rule = ruleRepository.saveAndFlush(new FinanceFeeRule(catalogue, structure, lineNumber,
                    line.description() == null || line.description().isBlank() ? catalogue.getName() : line.description(),
                    ruleVersion, command.transactionCurrencyCode(), line.amount(), ratedPrice.exchangeRate(), baseAmount,
                    command.effectiveFrom(), command.effectiveUntil(), actor));
            List<FinanceFeeRuleScope> scopes = createScopes(rule, structure);
            scopeRepository.saveAllAndFlush(scopes);
            lineNumber++;
        }
        return view(structure);
    }

    @Transactional
    public StructureSummary move(UUID structureId, String action, StructureDecision command, UUID actor) {
        FinanceFeeStructure structure = requireLocked(structureId);
        List<FinanceFeeRule> lines = ruleRepository
                .findAllByFeeStructureIdAndDeletedAtIsNullOrderByStructureLineNumberAsc(structureId);
        if (lines.isEmpty()) throw new IllegalStateException("A fee structure requires at least one line item.");
        Instant now = clock.instant();
        if ("activate".equals(action)) {
            for (FinanceFeeRule line : lines) {
                FinanceFeeCatalogue catalogue = line.getFeeCatalogue();
                if (catalogue.getStatus() == FinanceFeeCatalogue.Status.DRAFT) {
                    catalogue.activate(actor, now, command.reason(), catalogue.getVersion());
                    catalogueRepository.saveAndFlush(catalogue);
                } else if (catalogue.getStatus() != FinanceFeeCatalogue.Status.ACTIVE) {
                    throw new IllegalStateException("Retired line-item definitions cannot be activated in a fee structure.");
                }
                if (line.getStatus() == FinanceFeeRule.Status.PENDING_RATE) {
                    throw new IllegalStateException("Every line item must have effective exchange-rate evidence before activation.");
                }
                String signature = scopeRepository
                        .findAllByFeeRuleIdAndDeletedAtIsNullOrderByScopeDimensionAsc(line.getId()).stream()
                        .map(FinanceFeeRuleScope::canonicalPart).sorted().reduce((left, right) -> left + "|" + right)
                        .orElseThrow(() -> new IllegalStateException("Every fee structure line requires applicability scope evidence."));
                line.approve(actor, now, command.reason(), signature, line.getVersion());
                ruleRepository.saveAndFlush(line);
            }
            structure.activate(actor, now, command.reason(), command.expectedVersion());
        } else if ("retire".equals(action)) {
            for (FinanceFeeRule line : lines) {
                if (line.getStatus() == FinanceFeeRule.Status.APPROVED) {
                    line.retire(actor, now, command.reason(), line.getVersion());
                    ruleRepository.saveAndFlush(line);
                }
            }
            structure.retire(actor, now, command.reason(), command.expectedVersion());
        } else {
            throw new IllegalArgumentException("Unsupported fee-structure action.");
        }
        return view(structureRepository.saveAndFlush(structure));
    }

    @Transactional(readOnly = true)
    public StructureSummary resolve(ResolveStructure command) {
        List<StructureMatch> matches = structureRepository
                .findAllByStatusAndDeletedAtIsNull(FinanceFeeStructure.Status.ACTIVE).stream()
                .filter(structure -> structure.getFeeContext() == command.feeContext())
                .filter(structure -> matchesProgrammeLevel(structure, command))
                .filter(structure -> appliesAt(structure, command.effectiveAt()))
                .map(structure -> match(structure, command))
                .filter(Objects::nonNull)
                .toList();
        if (matches.isEmpty()) throw new IllegalStateException("No active fee structure matches this billing context.");
        int highestPriority = matches.stream().mapToInt(StructureMatch::priority).max().orElseThrow();
        List<StructureMatch> winners = matches.stream().filter(match -> match.priority() == highestPriority).toList();
        if (winners.size() != 1) {
            throw new IllegalStateException("Multiple fee structures have equal precedence. Finance must resolve the overlap.");
        }
        FinanceFeeStructure winner = winners.getFirst().structure();
        return view(winner, null);
    }

    private StructureMatch match(FinanceFeeStructure structure, ResolveStructure command) {
        if (structure.getFeeContext() == FinanceFeeStructure.FeeContext.ACADEMIC) {
            if (structure.getScopeType() == FinanceFeeStructure.ScopeType.PROGRAMME) {
                return Objects.equals(structure.getScopeReferenceId(), command.programmeId())
                        ? new StructureMatch(structure, 3000) : null;
            }
            if (structure.getScopeType() == FinanceFeeStructure.ScopeType.ACADEMIC_UNIT) {
                List<zw.ac.uz.emhare.finance.catalogue.api.model.FinanceFeeStructureApiModels.AcademicUnitPathItem> path = command.academicUnitPath() == null
                        ? List.of() : command.academicUnitPath();
                for (int distance = 0; distance < path.size(); distance++) {
                    var unit = path.get(distance);
                    if (Objects.equals(structure.getScopeReferenceId(), unit.id())
                            || sameCode(structure.getScopeReferenceCode(), unit.code())) {
                        return new StructureMatch(structure, 2000 - distance);
                    }
                }
                return null;
            }
            return structure.getScopeType() == FinanceFeeStructure.ScopeType.INSTITUTION
                    ? new StructureMatch(structure, 1000) : null;
        }
        if (structure.getFeeContext() == FinanceFeeStructure.FeeContext.APPLICATION) {
            UUID applicableReferenceId = structure.getScopeType() == FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL
                    ? command.programmeLevelId()
                    : structure.getScopeType() == FinanceFeeStructure.ScopeType.PROGRAMME_TYPE
                            ? command.programmeTypeId()
                            : null;
            if (!Objects.equals(structure.getScopeReferenceId(), applicableReferenceId)) return null;
            if (structure.getApplicantCategoryCode() != null
                    && !sameCode(structure.getApplicantCategoryCode(), command.applicantCategoryCode())) return null;
            return new StructureMatch(structure, structure.getApplicantCategoryCode() == null ? 1000 : 1100);
        }
        return structure.getScopeType() == FinanceFeeStructure.ScopeType.GLOBAL
                ? new StructureMatch(structure, 1000) : null;
    }

    private boolean matchesProgrammeLevel(FinanceFeeStructure structure, ResolveStructure command) {
        return (structure.getProgrammeLevelId() != null
                    && Objects.equals(structure.getProgrammeLevelId(), command.programmeLevelId()))
                || sameCode(structure.getProgrammeLevelCode(), command.programmeLevelCode());
    }

    private boolean appliesAt(FinanceFeeStructure structure, Instant effectiveAt) {
        return !effectiveAt.isBefore(structure.getEffectiveFrom())
                && (structure.getEffectiveUntil() == null || effectiveAt.isBefore(structure.getEffectiveUntil()));
    }

    private List<FinanceFeeRuleScope> createScopes(FinanceFeeRule rule, FinanceFeeStructure structure) {
        List<FinanceFeeRuleScope> scopes = new ArrayList<>();
        scopes.add(new FinanceFeeRuleScope(rule, FinanceFeeRuleScope.Dimension.PROGRAMME_LEVEL,
                structure.getProgrammeLevelId(), structure.getProgrammeLevelCode(), structure.getProgrammeLevelName()));
        if (structure.getFeeContext() == FinanceFeeStructure.FeeContext.ACADEMIC) {
            FinanceFeeRuleScope.Dimension dimension = switch (structure.getScopeType()) {
                case INSTITUTION -> FinanceFeeRuleScope.Dimension.INSTITUTION;
                case ACADEMIC_UNIT -> FinanceFeeRuleScope.Dimension.ACADEMIC_UNIT;
                case PROGRAMME -> FinanceFeeRuleScope.Dimension.PROGRAMME;
                default -> throw new IllegalStateException("Unsupported academic fee scope.");
            };
            scopes.add(new FinanceFeeRuleScope(rule, dimension, structure.getScopeReferenceId(),
                    structure.getScopeType() == FinanceFeeStructure.ScopeType.INSTITUTION
                            ? "INSTITUTION" : structure.getScopeReferenceCode(),
                    structure.getScopeType() == FinanceFeeStructure.ScopeType.INSTITUTION
                            ? "Institution" : structure.getScopeReferenceName()));
        } else if (structure.getFeeContext() == FinanceFeeStructure.FeeContext.APPLICATION) {
            FinanceFeeRuleScope.Dimension programmeDimension = structure.getScopeType()
                    == FinanceFeeStructure.ScopeType.PROGRAMME_LEVEL
                            ? FinanceFeeRuleScope.Dimension.PROGRAMME_LEVEL
                            : FinanceFeeRuleScope.Dimension.PROGRAMME_TYPE;
            if (programmeDimension != FinanceFeeRuleScope.Dimension.PROGRAMME_LEVEL) {
                scopes.add(new FinanceFeeRuleScope(rule, programmeDimension,
                        structure.getScopeReferenceId(), structure.getScopeReferenceCode(), structure.getScopeReferenceName()));
            }
            if (structure.getApplicantCategoryCode() != null) {
                scopes.add(new FinanceFeeRuleScope(rule, FinanceFeeRuleScope.Dimension.APPLICANT_CATEGORY,
                        null, structure.getApplicantCategoryCode(), structure.getApplicantCategoryCode()));
            }
        } else {
            scopes.add(new FinanceFeeRuleScope(rule, FinanceFeeRuleScope.Dimension.GLOBAL, null, null, null));
        }
        return scopes;
    }

    private FinanceFeeCatalogue resolveCatalogue(LineInput line, UUID actor) {
        if (line.feeCatalogueId() != null) {
            return catalogueRepository.findById(line.feeCatalogueId())
                    .filter(catalogue -> !catalogue.isDeleted())
                    .orElseThrow(() -> new IllegalArgumentException("Selected fee line definition was not found."));
        }
        if (line.feeCode() == null || line.feeCode().isBlank() || line.feeName() == null || line.feeName().isBlank()
                || line.chargeType() == null || line.receivableAccountCode() == null || line.receivableAccountCode().isBlank()
                || line.revenueAccountCode() == null || line.revenueAccountCode().isBlank()) {
            throw new IllegalArgumentException("A new fee line requires its code, name, charge type, and posting accounts.");
        }
        catalogueRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(line.feeCode()).ifPresent(existing -> {
            throw new IllegalStateException("Fee definition " + line.feeCode().trim().toUpperCase(Locale.ROOT)
                    + " already exists. Select the existing definition instead.");
        });
        return catalogueRepository.saveAndFlush(new FinanceFeeCatalogue(line.feeCode(), line.feeName(), line.description(),
                line.chargeType(), line.receivableAccountCode(), line.revenueAccountCode(), line.taxCode(), actor));
    }

    private RatedPrice rate(String currencyCode, Instant effectiveAt) {
        String normalizedCurrency = ExchangeRate.normalizeCurrencyCode(currencyCode);
        if ("USD".equals(normalizedCurrency)) return new RatedPrice(null);
        List<ExchangeRate> rates = exchangeRateRepository.findEffectiveRates(normalizedCurrency, effectiveAt);
        if (rates.size() > 1) throw new IllegalStateException("Multiple effective exchange rates require Finance correction.");
        return new RatedPrice(rates.isEmpty() ? null : rates.getFirst());
    }

    private void validateLineUniqueness(List<LineInput> lines) {
        Set<String> identities = new HashSet<>();
        for (LineInput line : lines) {
            String identity = line.feeCatalogueId() == null
                    ? "CODE:" + (line.feeCode() == null ? "" : line.feeCode().trim().toUpperCase(Locale.ROOT))
                    : "ID:" + line.feeCatalogueId();
            if (!identities.add(identity)) throw new IllegalArgumentException("A fee line item can appear only once in a structure.");
        }
    }

    private void validateAttachments(FinanceFeeStructure.FeeContext feeContext, List<AttachmentInput> attachments,
            BigDecimal structureTotal) {
        if (attachments.isEmpty()) return;
        if (feeContext != FinanceFeeStructure.FeeContext.ACADEMIC) {
            throw new IllegalArgumentException("Only academic fee structures can have programme-period attachments.");
        }
        Set<String> identities = new HashSet<>();
        for (AttachmentInput attachment : attachments) {
            String identity = attachment.programmeId() + "|" + attachment.academicPeriodId() + "|"
                    + attachment.programmePeriodNumber();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("A programme can have only one attachment for the same period and programme level.");
            }
            if (attachment.discountType() == FinanceFeeStructureAttachment.DiscountType.AMOUNT
                    && attachment.discountValue() != null
                    && attachment.discountValue().compareTo(structureTotal) > 0) {
                throw new IllegalArgumentException("Discount amount cannot exceed the complete fee structure total.");
            }
        }
    }

    private FinanceFeeStructure requireLocked(UUID id) {
        return structureRepository.findLockedByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Fee structure was not found."));
    }

    private StructureSummary view(FinanceFeeStructure structure) {
        return view(structure, null);
    }

    private StructureSummary view(FinanceFeeStructure structure, FinanceFeeStructureAttachment selectedAttachment) {
        List<StructureLineSummary> lines = ruleRepository
                .findAllByFeeStructureIdAndDeletedAtIsNullOrderByStructureLineNumberAsc(structure.getId()).stream()
                .map(rule -> new StructureLineSummary(rule.getId(), rule.getStructureLineNumber(),
                        rule.getFeeCatalogue().getId(), rule.getFeeCatalogue().getCode(), rule.getFeeCatalogue().getName(),
                        rule.getStructureLineDescription(), rule.getFeeCatalogue().getChargeType(),
                        rule.getFeeCatalogue().getReceivableAccountCode(), rule.getFeeCatalogue().getRevenueAccountCode(),
                        rule.getFeeCatalogue().getTaxCode(), rule.getTransactionAmount(), rule.getTransactionCurrencyCode(),
                        rule.getBaseAmount(), rule.getRatingStatus(), rule.getStatus()))
                .toList();
        BigDecimal structureTotal = lines.stream()
                .map(StructureLineSummary::transactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<StructureAttachmentSummary> attachments = structure.getId() == null ? List.of()
                : attachmentRepository.findAllByFeeStructureIdAndDeletedAtIsNullOrderByCreatedAtAsc(structure.getId()).stream()
                        .map(attachment -> attachmentView(attachment, structureTotal))
                        .toList();
        return new StructureSummary(structure.getId(), structure.getCode(), structure.getName(), structure.getDescription(),
                structure.getFeeContext(), structure.getScopeType(), structure.getScopeReferenceId(),
                structure.getScopeReferenceCode(), structure.getScopeReferenceName(), structure.getProgrammeLevelId(),
                structure.getProgrammeLevelCode(), structure.getProgrammeLevelName(), structure.getAcademicPeriodId(),
                structure.getAcademicPeriodCode(), structure.getAcademicPeriodName(), structure.getProgrammePeriodNumber(),
                structure.getApplicantCategoryCode(), structure.getTransactionCurrencyCode(), structure.getEffectiveFrom(),
                structure.getEffectiveUntil(), structure.getStatus(), structure.getPreparedByUserId(),
                structure.getActivatedByUserId(), structure.getActivatedAt(), structure.getVersion(), lines, attachments,
                selectedAttachment == null ? null : attachmentView(selectedAttachment, structureTotal));
    }

    private FinanceFeeStructureAttachment selectedAttachment(FinanceFeeStructure structure, ResolveStructure command) {
        if (structure.getFeeContext() != FinanceFeeStructure.FeeContext.ACADEMIC
                || command.programmeId() == null || command.academicPeriodId() == null
                || command.programmePeriodNumber() == null || structure.getId() == null) {
            return null;
        }
        return attachmentRepository.findAllByFeeStructureIdAndDeletedAtIsNullOrderByCreatedAtAsc(structure.getId()).stream()
                .filter(attachment -> attachment.matches(command.programmeId(), command.academicPeriodId(),
                        command.programmePeriodNumber()))
                .findFirst()
                .orElse(null);
    }

    private StructureAttachmentSummary attachmentView(FinanceFeeStructureAttachment attachment, BigDecimal structureTotal) {
        BigDecimal discountAmount = attachment.discountAmount(structureTotal);
        return new StructureAttachmentSummary(attachment.getId(), attachment.getProgrammeId(), attachment.getProgrammeCode(),
                attachment.getProgrammeName(), attachment.getAcademicPeriodId(), attachment.getAcademicPeriodCode(),
                attachment.getAcademicPeriodName(), attachment.getProgrammePeriodNumber(), attachment.getDiscountType(),
                attachment.getDiscountValue(), attachment.getDiscountReason(), discountAmount,
                structureTotal.subtract(discountAmount).max(BigDecimal.ZERO));
    }

    private boolean sameCode(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private record RatedPrice(ExchangeRate exchangeRate) { }
    private record StructureMatch(FinanceFeeStructure structure, int priority) { }
}
