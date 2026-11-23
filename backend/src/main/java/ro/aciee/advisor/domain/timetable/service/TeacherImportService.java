package ro.aciee.advisor.domain.timetable.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.aciee.advisor.domain.professor.service.ProfessorDirectory;
import ro.aciee.advisor.domain.timetable.model.TimetableProgram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherImportService {

    private static final Map<String, String> COMPONENTS = Map.of("C", "lecture", "S", "seminar", "L", "lab", "P", "project");

    private final JdbcClient jdbc;
    private final ProfessorDirectory professorDirectory;

    private record Offering(int id, String groupCode, String title, int lectureHours, int seminarHours, int labHours, int projectHours) {
        int hours(String component) {
            return switch (component) {
                case "lecture" -> lectureHours;
                case "seminar" -> seminarHours;
                case "lab" -> labHours;
                default -> projectHours;
            };
        }
    }

    private record Slot(int offeringId, String component) {
    }

    private record Applied(String teacher, int professorId) {
    }

    private record Professor(int id, String fullName) {
    }

    @Transactional
    public int importTeachers(List<TimetableProgram> programs) {
        var offerings = new HashMap<String, Offering>();
        jdbc.sql("""
                        SELECT o.id, g.code AS group_code, o.title_snapshot AS title,
                               ci.lecture_hours, ci.seminar_hours, ci.lab_hours, ci.project_hours
                        FROM core.course_offering o
                        JOIN core.study_group g ON g.id = o.group_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        JOIN core.curriculum_item ci ON ci.id = o.curriculum_item_id
                        WHERE ay.during @> current_date""")
                .query(Offering.class)
                .list()
                .forEach(o -> offerings.put(o.groupCode() + "|" + LessonNames.fold(o.title()), o));
        var teachers = new LinkedHashMap<Slot, Set<String>>();
        for (var program : programs) {
            for (var lesson : program.getLessons()) {
                String component = lesson.getKind() == null ? null : COMPONENTS.get(lesson.getKind());
                if (component == null || lesson.getTeacher() == null || lesson.getCourse() == null) {
                    continue;
                }
                for (String group : lesson.getGroups()) {
                    var offering = offerings.get(group + "|" + LessonNames.fold(lesson.getCourse()));
                    if (offering != null && offering.hours(component) > 0) {
                        teachers.computeIfAbsent(new Slot(offering.id(), component), k -> new TreeSet<>()).add(lesson.getTeacher().trim());
                    }
                }
            }
        }
        if (teachers.isEmpty()) {
            return 0;
        }
        var clusterOf = new HashMap<String, TeacherNames.Cluster>();
        TeacherNames.cluster(programs.stream().flatMap(p -> p.getLessons().stream())
                        .map(l -> l.getTeacher() == null ? null : l.getTeacher().trim()).filter(Objects::nonNull).toList())
                .forEach(c -> c.variants().forEach(v -> clusterOf.put(v.raw(), c)));
        var applied = new HashMap<Slot, Applied>();
        var aliases = new HashMap<String, Set<Integer>>();
        jdbc.sql("""
                        SELECT i.offering_id, i.component, i.teacher, i.professor_id, p.archived_at IS NULL AS active
                        FROM core.timetable_import i JOIN core.professor p ON p.id = i.professor_id""")
                .query((RowCallbackHandler) rs -> {
                    applied.put(new Slot(rs.getInt("offering_id"), rs.getString("component")),
                            new Applied(rs.getString("teacher"), rs.getInt("professor_id")));
                    if (rs.getBoolean("active")) {
                        for (String raw : rs.getString("teacher").split(" / ")) {
                            aliases.computeIfAbsent(raw, k -> new TreeSet<>()).add(rs.getInt("professor_id"));
                        }
                    }
                });
        var current = new HashMap<Slot, Integer>();
        jdbc.sql("""
                        SELECT DISTINCT ON (offering_id, component) offering_id, component, professor_id
                        FROM core.teaching_assignment
                        WHERE subgroup_id IS NULL AND valid_during && daterange(current_date, NULL) AND offering_id IN (:ids)
                        ORDER BY offering_id, component, lower(valid_during) DESC""")
                .param("ids", teachers.keySet().stream().map(Slot::offeringId).distinct().toList())
                .query((RowCallbackHandler) rs -> current.put(new Slot(rs.getInt("offering_id"), rs.getString("component")),
                        rs.getInt("professor_id")));
        var known = new ArrayList<>(jdbc.sql("SELECT id, full_name FROM core.professor WHERE archived_at IS NULL")
                .query(Professor.class)
                .list());
        var resolved = new IdentityHashMap<TeacherNames.Cluster, Integer>();
        int changed = 0;
        for (var entry : teachers.entrySet()) {
            var slot = entry.getKey();
            var found = entry.getValue().stream().map(clusterOf::get).filter(Objects::nonNull).distinct().toList();
            if (found.size() != 1) {
                continue;
            }
            String teacher = String.join(" / ", entry.getValue());
            var previous = applied.get(slot);
            if (previous != null && previous.teacher().equals(teacher)) {
                continue;
            }
            Integer now = current.get(slot);
            if (now != null && (previous == null || now != previous.professorId())) {
                continue;
            }
            var cluster = found.getFirst();
            if (!resolved.containsKey(cluster)) {
                resolved.put(cluster, resolve(cluster, aliases, known));
            }
            Integer professorId = resolved.get(cluster);
            if (professorId == null) {
                continue;
            }
            if (!professorId.equals(now)) {
                end(slot);
                start(slot, professorId);
                changed++;
            }
            jdbc.sql("""
                            INSERT INTO core.timetable_import (offering_id, component, teacher, professor_id)
                            VALUES (:offering, :component, :teacher, :professor)
                            ON CONFLICT (offering_id, component) DO UPDATE
                            SET teacher = excluded.teacher, professor_id = excluded.professor_id, applied_at = now()""")
                    .param("offering", slot.offeringId())
                    .param("component", slot.component())
                    .param("teacher", teacher)
                    .param("professor", professorId)
                    .update();
        }
        return changed;
    }

    private Integer resolve(TeacherNames.Cluster cluster, Map<String, Set<Integer>> aliases, List<Professor> known) {
        var seen = cluster.variants().stream().map(v -> aliases.get(v.raw())).filter(Objects::nonNull)
                .flatMap(Set::stream).collect(Collectors.toSet());
        if (seen.size() == 1) {
            return seen.iterator().next();
        }
        var matches = known.stream().filter(p -> cluster.matches(p.fullName())).map(Professor::id).distinct().toList();
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (matches.size() > 1) {
            log.info("Timetable teacher {} matches {} professors, left for a leader", cluster.displayName(), matches.size());
            return null;
        }
        int id = professorDirectory.create(cluster.displayName(), cluster.title());
        known.add(new Professor(id, cluster.displayName()));
        return id;
    }

    private void end(Slot slot) {
        var match = """
                offering_id = :offering AND component = :component AND subgroup_id IS NULL
                  AND valid_during && daterange(current_date, NULL)""";
        jdbc.sql("DELETE FROM core.teaching_assignment WHERE lower(valid_during) >= current_date AND " + match)
                .param("offering", slot.offeringId())
                .param("component", slot.component())
                .update();
        jdbc.sql("UPDATE core.teaching_assignment SET valid_during = daterange(lower(valid_during), current_date) WHERE " + match)
                .param("offering", slot.offeringId())
                .param("component", slot.component())
                .update();
    }

    private void start(Slot slot, int professorId) {
        jdbc.sql("""
                        INSERT INTO core.teaching_assignment (offering_id, group_id, professor_id, component, valid_during)
                        SELECT o.id, o.group_id, :professor, :component,
                               daterange(greatest(current_date, lower(ay.during)), upper(ay.during))
                        FROM core.course_offering o
                        JOIN core.study_group g ON g.id = o.group_id
                        JOIN core.academic_year ay ON ay.id = g.academic_year_id
                        WHERE o.id = :offering""")
                .param("offering", slot.offeringId())
                .param("component", slot.component())
                .param("professor", professorId)
                .update();
    }
}
