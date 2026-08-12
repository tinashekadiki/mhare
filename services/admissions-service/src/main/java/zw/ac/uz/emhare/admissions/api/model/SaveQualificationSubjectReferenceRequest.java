package zw.ac.uz.emhare.admissions.api.model;

import zw.ac.uz.emhare.admissions.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @author Tinashe K */
public record SaveQualificationSubjectReferenceRequest(
        @NotBlank @Size(max = 30) String level,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String subjectGroupCode,
        boolean scienceSubject,
        boolean active,
        long expectedVersion) {
}
