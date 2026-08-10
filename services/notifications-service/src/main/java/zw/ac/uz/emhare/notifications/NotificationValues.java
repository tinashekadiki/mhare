package zw.ac.uz.emhare.notifications;

/** @author Tinashe K */
final class NotificationValues {
    private NotificationValues() {}
    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
    static String code(String value, String field) { return required(value, field).toUpperCase().replace(' ', '_'); }
    static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    static String reason(String value, String field) {
        String reason = required(value, field);
        if (reason.length() < 10) throw new IllegalArgumentException(field + " must contain at least 10 characters.");
        if (reason.length() > 1000) throw new IllegalArgumentException(field + " must not exceed 1000 characters.");
        return reason;
    }
    static void version(long actual, long expected, String record) {
        if (actual != expected) throw new IllegalStateException(record + " was changed by another user. Refresh and try again.");
    }
}
