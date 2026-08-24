package zw.ac.uz.emhare.admissions.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import zw.ac.uz.emhare.admissions.*;

/**
 * @author Tinashe K
 */
public record SaveQualificationSubjectReferenceRequest(
    @NotBlank @Size(max = 30) String level,
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 50) String subjectGroupCode,
    boolean scienceSubject,
    boolean mathematicsSubject,
    boolean englishSubject,
    boolean active,
    long expectedVersion) {}
