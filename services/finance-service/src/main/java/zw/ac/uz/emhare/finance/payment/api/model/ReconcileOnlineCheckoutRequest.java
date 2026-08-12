package zw.ac.uz.emhare.finance.payment.api.model;

import java.util.UUID;

/** Optional online-checkout attempt selection for reconciliation. @author Tinashe K */
public record ReconcileOnlineCheckoutRequest(UUID attemptId) {
}
