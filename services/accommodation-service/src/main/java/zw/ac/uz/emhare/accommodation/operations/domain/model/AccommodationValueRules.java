package zw.ac.uz.emhare.accommodation.operations.domain.model;

import zw.ac.uz.emhare.accommodation.operations.*;

import java.util.Locale;

/** @author Tinashe K */
final class AccommodationValueRules {
    private AccommodationValueRules() {}

    static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String code(String value, String label) {
        return required(value, label).toUpperCase(Locale.ROOT);
    }

    static void requireVersion(long actualVersion, long expectedVersion, String recordName) {
        if (actualVersion != expectedVersion) {
            throw new IllegalStateException(recordName + " was changed by another operator. Refresh and try again.");
        }
    }
}
