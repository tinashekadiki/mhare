package zw.ac.uz.emhare.finance.catalogue.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Approved student discount values applied during billing. @author Tinashe K */
public record AppliedStudentDiscount(UUID id, String code, BigDecimal percentage) {
}
