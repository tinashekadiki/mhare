package zw.ac.uz.emhare.admissions.application.command;

import java.util.List;
import java.util.UUID;

/** One applicant-confirmed qualification sitting or award and its results. @author Tinashe K */
public record SaveQualificationAggregateCommand(
    String level,
    String awardTypeCode,
    String qualificationName,
    UUID examBodyId,
    String institutionName,
    String centreNumber,
    String candidateNumber,
    Integer yearWritten,
    Integer durationMonths,
    UUID countryId,
    UUID documentId,
    List<CreateQualificationResultCommand> results,
    long expectedVersion) {}
