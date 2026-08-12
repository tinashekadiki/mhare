package zw.ac.uz.emhare.admissions.application.command;

import zw.ac.uz.emhare.admissions.application.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** @author Tinashe K */
public record CreateAdmissionOfferCommand(
        UUID offerBatchId,
        UUID programmeChoiceId,
        String offerType,
        String conditionsText,
        Instant acceptanceDeadline,
        LocalDate registrationDate,
        LocalDate orientationDate,
        LocalDate commencementDate,
        List<Condition> conditions,
        UUID actorUserId) {

    public record Condition(String code, String description, boolean required) {
    }
}
