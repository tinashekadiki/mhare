package zw.ac.uz.emhare.admissions.application.command;

import zw.ac.uz.emhare.admissions.application.*;

import java.util.UUID;

/** One managed subject result captured as part of an applicant qualification batch. @author Tinashe K */
public record CreateQualificationResultCommand(
        UUID subjectId,
        String grade,
        Boolean principalSubject) {
}
