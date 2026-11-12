package ro.aciee.advisor.domain.timetable.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.aciee.advisor.domain.timetable.model.Lesson;
import ro.aciee.advisor.domain.timetable.model.TimetableChange;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TimetableChangeService {

    private static final String COLUMNS = "group_code, day, start_time, end_time, weeks, kind, subject, room, teacher";
    private static final String VALUES = ":group, :day, :start, :end, :weeks, :kind, :subject, :room, :teacher";

    private final JdbcClient jdbc;

    private record Slot(String groupCode, int day, String startTime, String endTime, String weeks, String kind,
                        String subject, String room, String teacher) {
    }

    @Transactional
    public int record(TimetableProgram program) {
        Set<Slot> stored = new HashSet<>(jdbc.sql("SELECT " + COLUMNS + " FROM core.timetable_slot WHERE program_code = :program")
                .param("program", program.getCode())
                .query(Slot.class)
                .list());
        Set<Slot> fresh = new LinkedHashSet<>();
        for (Lesson lesson : program.getLessons()) {
            for (String group : lesson.getGroups()) {
                fresh.add(new Slot(group, lesson.getDay(), lesson.getStart(), lesson.getEnd(), lesson.getWeeks().name(),
                        lesson.getKind(), lesson.getSubject(), lesson.getRoom(), lesson.getTeacher()));
            }
        }
        if (stored.equals(fresh) || fresh.size() * 2 < stored.size()) {
            return 0;
        }
        int changes = 0;
        if (!stored.isEmpty()) {
            for (Slot slot : fresh) {
                if (!stored.contains(slot)) {
                    insertChange(program.getCode(), slot, "added");
                    changes++;
                }
            }
            for (Slot slot : stored) {
                if (!fresh.contains(slot)) {
                    insertChange(program.getCode(), slot, "removed");
                    changes++;
                }
            }
        }
        jdbc.sql("DELETE FROM core.timetable_change WHERE detected_at < now() - interval '90 days'").update();
        jdbc.sql("DELETE FROM core.timetable_slot WHERE program_code = :program").param("program", program.getCode()).update();
        for (Slot slot : fresh) {
            bind(jdbc.sql("INSERT INTO core.timetable_slot (program_code, " + COLUMNS + ") VALUES (:program, " + VALUES + ")"),
                    program.getCode(), slot).update();
        }
        return changes;
    }

    @Transactional(readOnly = true)
    public List<TimetableChange> recent(String program, String group) {
        return jdbc.sql("""
                        SELECT change, detected_at, day, start_time, end_time, weeks, kind, subject, room, teacher
                        FROM core.timetable_change
                        WHERE program_code = :program AND group_code = :group AND detected_at > now() - interval '21 days'
                        ORDER BY detected_at DESC, day, start_time
                        LIMIT 50""")
                .param("program", program)
                .param("group", group)
                .query(TimetableChange.class)
                .list();
    }

    private void insertChange(String program, Slot slot, String change) {
        bind(jdbc.sql("INSERT INTO core.timetable_change (program_code, " + COLUMNS + ", change) VALUES (:program, " + VALUES + ", :change)"),
                program, slot)
                .param("change", change)
                .update();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, String program, Slot slot) {
        return statement.param("program", program)
                .param("group", slot.groupCode())
                .param("day", slot.day())
                .param("start", slot.startTime())
                .param("end", slot.endTime())
                .param("weeks", slot.weeks())
                .param("kind", slot.kind())
                .param("subject", slot.subject())
                .param("room", slot.room())
                .param("teacher", slot.teacher());
    }
}
