package zw.ac.uz.emhare.common.messaging;

/**
 * Versioned integration-event topology shared by service producers and consumers.
 *
 * @author Tinashe K
 */
public final class EmhareMessagingTopology {

    public static final String EVENTS_EXCHANGE = "emhare.events";
    public static final String DEAD_LETTER_EXCHANGE = "emhare.events.dlx";

    public static final String APPLICATION_FEE_REQUIRED_EVENT = "admissions.application-fee-required.v1";
    public static final String APPLICATION_FEE_REQUIRED_QUEUE = "finance.application-fee-required.v1";

    public static final String PAYMENT_REFERENCE_UPDATED_EVENT = "finance.application-payment-reference-updated.v1";
    public static final String PAYMENT_REFERENCE_UPDATED_QUEUE = "admissions.application-payment-reference-updated.v1";

    public static final String ACCEPTED_OFFER_READY_FOR_CONVERSION_EVENT =
            "admissions.accepted-offer-ready-for-conversion.v1";
    public static final String ACCEPTED_OFFER_READY_FOR_CONVERSION_QUEUE =
            "student-records.accepted-offer-ready-for-conversion.v1";

    public static final String STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_EVENT =
            "student-records.finance-account-provisioning-requested.v1";
    public static final String STUDENT_FINANCE_ACCOUNT_PROVISIONING_REQUESTED_QUEUE =
            "finance.student-account-provisioning-requested.v1";
    public static final String STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_EVENT =
            "student-records.portal-access-provisioning-requested.v1";
    public static final String STUDENT_PORTAL_ACCESS_PROVISIONING_REQUESTED_QUEUE =
            "core-identity.student-portal-access-provisioning-requested.v1";

    public static final String STUDENT_FINANCE_ACCOUNT_PROVISIONED_EVENT =
            "finance.student-account-provisioned.v1";
    public static final String STUDENT_FINANCE_ACCOUNT_PROVISIONED_QUEUE =
            "student-records.finance-account-provisioned.v1";
    public static final String STUDENT_PORTAL_ACCESS_PROVISIONED_EVENT =
            "core-identity.student-portal-access-provisioned.v1";
    public static final String STUDENT_PORTAL_ACCESS_PROVISIONED_QUEUE =
            "student-records.portal-access-provisioned.v1";

    public static final String STUDENT_CONVERSION_COMPLETED_EVENT =
            "student-records.conversion-completed.v1";
    public static final String STUDENT_CONVERSION_COMPLETED_QUEUE =
            "admissions.student-conversion-completed.v1";

    public static final String STUDENT_REGISTRATION_CONFIRMED_EVENT =
            "student-records.registration-confirmed.v1";
    public static final String STUDENT_REGISTRATION_CONFIRMED_ASSESSMENT_QUEUE =
            "assessment-results.registration-confirmed.v1";
    public static final String STUDENT_REGISTRATION_CONFIRMED_EXAMS_QUEUE =
            "exams-timetabling.registration-confirmed.v1";
    public static final String STUDENT_REGISTRATION_CONFIRMED_FINANCE_QUEUE =
            "finance.registration-confirmed.v1";

    public static final String PUBLISHED_RESULT_VERSION_CREATED_EVENT =
            "assessment-results.published-result-version-created.v1";
    public static final String PUBLISHED_RESULT_VERSION_CREATED_DOCUMENTS_QUEUE =
            "documents-reporting.published-result-version-created.v1";
    public static final String PROGRESSION_DECISION_PUBLISHED_EVENT =
            "assessment-results.progression-decision-published.v1";
    public static final String PROGRESSION_DECISION_PUBLISHED_DOCUMENTS_QUEUE =
            "documents-reporting.progression-decision-published.v1";

    public static final String NOTIFICATION_REQUESTED_EVENT = "notifications.requested.v1";
    public static final String NOTIFICATION_REQUESTED_QUEUE = "notifications.requested.v1";

    public static final String DOCUMENT_VERIFICATION_CHANGED_EVENT =
            "documents-reporting.document-verification-changed.v1";
    public static final String DOCUMENT_VERIFICATION_CHANGED_ADMISSIONS_QUEUE =
            "admissions.document-verification-changed.v1";
    public static final String MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_EVENT =
            "admissions.missing-application-document-workflow-requested.v1";
    public static final String MISSING_APPLICATION_DOCUMENT_WORKFLOW_REQUESTED_CORE_QUEUE =
            "core-identity.missing-application-document-workflow-requested.v1";
    public static final String ACADEMIC_REVIEW_RELEASED_EVENT =
            "admissions.academic-review-released.v1";
    public static final String ACADEMIC_REVIEW_RELEASED_CORE_QUEUE =
            "core-identity.academic-review-released.v1";
    public static final String ACADEMIC_RECOMMENDATION_RECORDED_EVENT =
            "admissions.academic-recommendation-recorded.v1";
    public static final String ACADEMIC_RECOMMENDATION_RECORDED_CORE_QUEUE =
            "core-identity.academic-recommendation-recorded.v1";
    public static final String OFFER_LETTER_REQUESTED_EVENT =
            "admissions.offer-letter-requested.v1";
    public static final String OFFER_LETTER_REQUESTED_DOCUMENTS_QUEUE =
            "documents-reporting.offer-letter-requested.v1";
    public static final String OFFER_LETTER_STORED_EVENT =
            "documents-reporting.offer-letter-stored.v1";
    public static final String OFFER_LETTER_STORED_ADMISSIONS_QUEUE =
            "admissions.offer-letter-stored.v1";
    public static final String OFFER_PUBLICATION_EVENT = "admissions.offer-publication.v1";
    public static final String OFFER_PUBLICATION_DOCUMENTS_QUEUE = "documents-reporting.offer-publication.v1";
    public static final String NOTIFICATION_DELIVERY_EVENT = "notifications.delivery.v1";
    public static final String NOTIFICATION_DELIVERY_ADMISSIONS_QUEUE = "admissions.notification-delivery.v1";

    private EmhareMessagingTopology() {
    }

    public static String deadLetterQueue(String queueName) {
        return queueName + ".dead";
    }

    public static String deadLetterRoutingKey(String queueName) {
        return queueName + ".dead";
    }
}
