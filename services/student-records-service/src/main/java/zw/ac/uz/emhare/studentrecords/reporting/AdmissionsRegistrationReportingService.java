package zw.ac.uz.emhare.studentrecords.reporting;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.uz.emhare.studentrecords.reporting.infrastructure.persistence.AdmissionsRegistrationReportingRepository;

/** Owns the Student Records side of accepted-versus-registered reporting. @author Tinashe K */
@Service
public class AdmissionsRegistrationReportingService {

    private final AdmissionsRegistrationReportingRepository repository;

    public AdmissionsRegistrationReportingService(AdmissionsRegistrationReportingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AdmissionsRegistrationOutcome> outcomes() {
        return repository.findOutcomes();
    }
}
