package zw.ac.uz.emhare.examstimetabling.setup.infrastructure.persistence;

import zw.ac.uz.emhare.examstimetabling.setup.domain.model.ExamSession;

import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.setup.*;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence adapter. @author Tinashe K */
public interface ExamSessionRepository extends JpaRepository<ExamSession,UUID> {
    List<ExamSession> findAllByDeletedAtIsNullOrderByStartsOnDescCodeAsc();
}
