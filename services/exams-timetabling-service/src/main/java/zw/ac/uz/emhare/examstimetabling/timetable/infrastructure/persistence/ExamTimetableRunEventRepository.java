package zw.ac.uz.emhare.examstimetabling.timetable.infrastructure.persistence;

import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.ExamTimetableRunEvent;

import zw.ac.uz.emhare.examstimetabling.invigilation.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.roster.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.setup.domain.model.*;
import zw.ac.uz.emhare.examstimetabling.timetable.*;
import zw.ac.uz.emhare.examstimetabling.timetable.domain.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data persistence adapter. @author Tinashe K */
public interface ExamTimetableRunEventRepository extends JpaRepository<ExamTimetableRunEvent,UUID> {}
