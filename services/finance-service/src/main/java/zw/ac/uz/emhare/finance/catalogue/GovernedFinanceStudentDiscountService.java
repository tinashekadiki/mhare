package zw.ac.uz.emhare.finance.catalogue;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.AppliedDiscount;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.CreateDiscount;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.DiscountDecision;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.DiscountRegister;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.DiscountSummary;
import zw.ac.uz.emhare.finance.catalogue.FinanceStudentDiscountContracts.ResolveDiscount;

/** Resolves one discount from explicit student and programme applicability fields. @author Tinashe K */
@Service
public class GovernedFinanceStudentDiscountService {
    private final FinanceStudentDiscountRuleRepository discountRepository;
    private final FinanceFeeCatalogueRepository catalogueRepository;
    private final Clock clock;

    public GovernedFinanceStudentDiscountService(FinanceStudentDiscountRuleRepository discountRepository,
            FinanceFeeCatalogueRepository catalogueRepository, Clock clock) {
        this.discountRepository = discountRepository;
        this.catalogueRepository = catalogueRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DiscountRegister register() {
        return new DiscountRegister(discountRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::view).toList());
    }

    @Transactional
    public DiscountSummary create(CreateDiscount command, UUID actor) {
        discountRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(command.code()).ifPresent(existing -> {
            throw new IllegalStateException("Student discount code already exists.");
        });
        FinanceFeeCatalogue catalogue = null;
        if (command.targetType() == FinanceStudentDiscountRule.TargetType.FEE_LINE) {
            if (command.feeCatalogueId() == null) {
                throw new IllegalArgumentException("A fee-line discount requires one fee definition.");
            }
            catalogue = catalogueRepository.findById(command.feeCatalogueId())
                    .filter(item -> !item.isDeleted())
                    .orElseThrow(() -> new IllegalArgumentException("Selected fee definition was not found."));
        }
        FinanceStudentDiscountRule discount = new FinanceStudentDiscountRule(
                command.code(), command.name(),
                command.academicUnitId(), command.academicUnitCode(), command.academicUnitName(),
                command.academicUnitDepth(), command.programmeId(), command.programmeCode(), command.programmeName(),
                command.programmeLevelId(), command.programmeLevelCode(), command.programmeLevelName(),
                command.programmeStudyLevel(), command.targetType(), catalogue, command.discountPercentage(),
                command.authorityReference(), command.effectiveFrom(), command.effectiveUntil(), actor);
        return view(discountRepository.saveAndFlush(discount));
    }

    @Transactional
    public DiscountSummary move(UUID id, String action, DiscountDecision command, UUID actor) {
        FinanceStudentDiscountRule discount = discountRepository.findLockedByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Student discount was not found."));
        Instant now = clock.instant();
        if ("activate".equals(action)) {
            ensureNoEqualPriorityOverlap(discount);
            discount.activate(actor, now, command.reason(), command.expectedVersion());
        } else if ("retire".equals(action)) {
            discount.retire(actor, now, command.reason(), command.expectedVersion());
        } else {
            throw new IllegalArgumentException("Unsupported student-discount action.");
        }
        return view(discountRepository.saveAndFlush(discount));
    }

    @Transactional(readOnly = true)
    public Optional<AppliedDiscount> resolve(ResolveDiscount command) {
        List<DiscountMatch> matches = discountRepository
                .findAllByStatusAndDeletedAtIsNull(FinanceStudentDiscountRule.Status.ACTIVE).stream()
                .filter(rule -> rule.appliesAt(command.effectiveAt()))
                .filter(rule -> programmeLevelMatches(rule, command))
                .filter(rule -> rule.getProgrammeStudyLevel().equals(command.programmeStudyLevel()))
                .filter(rule -> rule.getProgrammeId() == null
                        || rule.getProgrammeId().equals(command.programmeId()))
                .filter(rule -> rule.getAcademicUnitId() == null
                        || rule.getAcademicUnitId().equals(command.academicUnitId()))
                .filter(rule -> rule.getTargetType() == FinanceStudentDiscountRule.TargetType.ALL_FEES
                        || Objects.equals(rule.getFeeCatalogue().getId(), command.feeCatalogueId()))
                .map(rule -> new DiscountMatch(rule, priority(rule)))
                .sorted(Comparator.comparingInt(DiscountMatch::priority).reversed())
                .toList();
        if (matches.isEmpty()) return Optional.empty();
        int winningPriority = matches.getFirst().priority();
        List<DiscountMatch> winners = matches.stream().filter(match -> match.priority() == winningPriority).toList();
        if (winners.size() != 1) {
            throw new IllegalStateException(
                    "Multiple active student discounts have equal priority. Finance must retire the overlap.");
        }
        FinanceStudentDiscountRule winner = winners.getFirst().rule();
        return Optional.of(new AppliedDiscount(winner.getId(), winner.getCode(), winner.getDiscountPercentage()));
    }

    private boolean programmeLevelMatches(FinanceStudentDiscountRule rule, ResolveDiscount command) {
        return rule.getProgrammeLevelId().equals(command.programmeLevelId())
                && rule.getProgrammeLevelCode().equalsIgnoreCase(command.programmeLevelCode());
    }

    private int priority(FinanceStudentDiscountRule rule) {
        int scopePriority = rule.getProgrammeId() != null ? 3000
                : rule.getAcademicUnitId() != null ? 2000 : 1000;
        int targetPriority = rule.getTargetType() == FinanceStudentDiscountRule.TargetType.FEE_LINE ? 100 : 0;
        return scopePriority + targetPriority;
    }

    private void ensureNoEqualPriorityOverlap(FinanceStudentDiscountRule candidate) {
        for (FinanceStudentDiscountRule active : discountRepository
                .findAllByStatusAndDeletedAtIsNull(FinanceStudentDiscountRule.Status.ACTIVE)) {
            if (!effectiveWindowsOverlap(active, candidate)
                    || !sameProgrammeLevel(active, candidate)
                    || !active.getProgrammeStudyLevel().equals(candidate.getProgrammeStudyLevel())
                    || !sameTarget(active, candidate)
                    || priority(active) != priority(candidate)
                    || !scopeOverlaps(active, candidate)) {
                continue;
            }
            throw new IllegalStateException(
                    "An active student discount already has equal priority for this programme level, study level, fee target, scope, and effective window.");
        }
    }

    private boolean sameProgrammeLevel(FinanceStudentDiscountRule left, FinanceStudentDiscountRule right) {
        return left.getProgrammeLevelId().equals(right.getProgrammeLevelId())
                && left.getProgrammeLevelCode().equalsIgnoreCase(right.getProgrammeLevelCode());
    }

    private boolean sameTarget(FinanceStudentDiscountRule left, FinanceStudentDiscountRule right) {
        return left.getTargetType() == right.getTargetType()
                && (left.getTargetType() == FinanceStudentDiscountRule.TargetType.ALL_FEES
                    || Objects.equals(left.getFeeCatalogue().getId(), right.getFeeCatalogue().getId()));
    }

    private boolean scopeOverlaps(FinanceStudentDiscountRule left, FinanceStudentDiscountRule right) {
        if (left.getProgrammeId() != null || right.getProgrammeId() != null) {
            return Objects.equals(left.getProgrammeId(), right.getProgrammeId())
                    && nullableScopeOverlaps(left.getAcademicUnitId(), right.getAcademicUnitId());
        }
        if (left.getAcademicUnitId() != null || right.getAcademicUnitId() != null) {
            return Objects.equals(left.getAcademicUnitId(), right.getAcademicUnitId());
        }
        return true;
    }

    private boolean nullableScopeOverlaps(UUID left, UUID right) {
        return left == null || right == null || left.equals(right);
    }

    private boolean effectiveWindowsOverlap(FinanceStudentDiscountRule left, FinanceStudentDiscountRule right) {
        Instant leftEnd = left.getEffectiveUntil() == null ? Instant.MAX : left.getEffectiveUntil();
        Instant rightEnd = right.getEffectiveUntil() == null ? Instant.MAX : right.getEffectiveUntil();
        return left.getEffectiveFrom().isBefore(rightEnd) && right.getEffectiveFrom().isBefore(leftEnd);
    }

    private DiscountSummary view(FinanceStudentDiscountRule discount) {
        FinanceFeeCatalogue catalogue = discount.getFeeCatalogue();
        return new DiscountSummary(discount.getId(), discount.getCode(), discount.getName(), discount.getScopeType(),
                discount.getAcademicUnitId(), discount.getAcademicUnitCode(), discount.getAcademicUnitName(),
                discount.getScopeDepth(), discount.getProgrammeId(), discount.getProgrammeCode(),
                discount.getProgrammeName(), discount.getProgrammeLevelId(), discount.getProgrammeLevelCode(),
                discount.getProgrammeLevelName(), discount.getProgrammeStudyLevel(), discount.getTargetType(),
                catalogue == null ? null : catalogue.getId(), catalogue == null ? null : catalogue.getCode(),
                catalogue == null ? null : catalogue.getName(), discount.getDiscountPercentage(),
                discount.getAuthorityReference(), discount.getEffectiveFrom(), discount.getEffectiveUntil(),
                discount.getStatus(), discount.getPreparedByUserId(), discount.getActivatedByUserId(),
                discount.getActivatedAt(), discount.getVersion());
    }

    private record DiscountMatch(FinanceStudentDiscountRule rule, int priority) { }
}
