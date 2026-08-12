package zw.ac.uz.emhare.admissions.application;

import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.api.model.ConfigureAdmissionQuotasRequest;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionQuota;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionQuotaRepository;
import zw.ac.uz.emhare.common.persistence.EmhareRevisionContext;

/** Maintains intake Programme quotas without using them as admission decision gates. @author Tinashe K */
@Service
public class AdmissionQuotaConfigurationService {
    private final AdmissionQuotaRepository repository;

    public AdmissionQuotaConfigurationService(AdmissionQuotaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<AdmissionQuotaSummary> configure(
            UUID intakeId,
            UUID actorUserId,
            ConfigureAdmissionQuotasRequest request) {
        requireDistinctScopes(request.quotas());
        String correlationId = EmhareRevisionContext.getCorrelationId().orElse(null);
        EmhareRevisionContext.setRequestMetadata(correlationId, request.changeReason());
        try {
            Map<QuotaScope, AdmissionQuota> existingByScope = new LinkedHashMap<>();
            repository.findAllByIntakeIdAndDeletedAtIsNullOrderByProgrammeCodeAscQuotaTypeCodeAsc(intakeId)
                    .forEach(quota -> existingByScope.put(scope(quota.getProgrammeId(), quota.getQuotaTypeCode()), quota));
            Set<QuotaScope> requestedScopes = new HashSet<>();
            for (ConfigureAdmissionQuotasRequest.QuotaInput input : request.quotas()) {
                QuotaScope requestedScope = scope(input.programmeId(), input.quotaTypeCode());
                requestedScopes.add(requestedScope);
                AdmissionQuota quota = existingByScope.get(requestedScope);
                if (quota == null) {
                    quota = new AdmissionQuota(
                            intakeId, input.programmeId(), input.programmeCode(), input.programmeName(),
                            input.quotaTypeCode(), input.capacity(), input.reservedCapacity());
                    existingByScope.put(requestedScope, quota);
                } else {
                    quota.configure(
                            input.programmeCode(), input.programmeName(), input.quotaTypeCode(),
                            input.capacity(), input.reservedCapacity(), input.expectedVersion());
                }
            }
            existingByScope.entrySet().stream()
                    .filter(entry -> !requestedScopes.contains(entry.getKey()))
                    .forEach(entry -> entry.getValue().markDeleted(actorUserId));
            repository.saveAllAndFlush(List.copyOf(existingByScope.values()));
            return current(intakeId);
        } finally {
            EmhareRevisionContext.setRequestMetadata(correlationId, null);
        }
    }

    @Transactional
    public List<AdmissionQuotaSummary> current(UUID intakeId) {
        return repository.findAllByIntakeIdAndDeletedAtIsNullOrderByProgrammeCodeAscQuotaTypeCodeAsc(intakeId)
                .stream().map(this::summary).toList();
    }

    private void requireDistinctScopes(List<ConfigureAdmissionQuotasRequest.QuotaInput> inputs) {
        Set<QuotaScope> scopes = new HashSet<>();
        if (inputs.stream().map(input -> scope(input.programmeId(), input.quotaTypeCode())).anyMatch(scope -> !scopes.add(scope))) {
            throw new IllegalArgumentException("Programme quota scopes must be distinct within an intake.");
        }
    }

    private QuotaScope scope(UUID programmeId, String quotaTypeCode) {
        return new QuotaScope(programmeId, quotaTypeCode.trim().toUpperCase(Locale.ROOT));
    }

    private AdmissionQuotaSummary summary(AdmissionQuota quota) {
        return new AdmissionQuotaSummary(
                quota.getId(), quota.getIntakeId(), quota.getProgrammeId(), quota.getProgrammeCode(),
                quota.getProgrammeName(), quota.getQuotaTypeCode(), quota.getCapacity(),
                quota.getReservedCapacity(), quota.getVersion());
    }

    private record QuotaScope(UUID programmeId, String quotaTypeCode) { }
}
