package zw.ac.uz.emhare.studentrecords.conversion.infrastructure.persistence;

import zw.ac.uz.emhare.studentrecords.conversion.domain.model.StudentStatusEvent;

import zw.ac.uz.emhare.studentrecords.conversion.*;
import zw.ac.uz.emhare.studentrecords.conversion.domain.model.*;
import zw.ac.uz.emhare.studentrecords.infrastructure.messaging.model.*;
import zw.ac.uz.emhare.studentrecords.registration.domain.model.*;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface StudentStatusEventRepository extends JpaRepository<StudentStatusEvent, UUID> {
}
