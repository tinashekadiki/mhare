package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.OfferBatch;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface OfferBatchRepository extends JpaRepository<OfferBatch, UUID> {
    List<OfferBatch> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
}
