package ro.aciee.advisor.domain.professor.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import ro.aciee.advisor.domain.timetable.service.LessonNames;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ProfessorDirectory {

    private final JdbcClient jdbc;

    public int create(String fullName, String titleCode) {
        String base = slugBase(fullName);
        var taken = new HashSet<>(jdbc.sql("SELECT slug FROM core.professor WHERE slug = :base OR slug LIKE :prefix")
                .param("base", base)
                .param("prefix", base + "-%")
                .query(String.class)
                .list());
        String slug = base;
        for (int n = 2; taken.contains(slug); n++) {
            slug = base + "-" + n;
        }
        jdbc.sql("""
                        INSERT INTO core.department (faculty_id, name_ro)
                        SELECT id, 'Nespecificat' FROM core.faculty WHERE code = 'ACIEE'
                        ON CONFLICT (faculty_id, name_ro) DO NOTHING""")
                .update();
        return jdbc.sql("""
                        INSERT INTO core.professor (full_name, title_code, department_id, slug, name_confirmed)
                        SELECT :name, :title, d.id, :slug, false
                        FROM core.department d JOIN core.faculty f ON f.id = d.faculty_id
                        WHERE f.code = 'ACIEE' AND d.name_ro = 'Nespecificat'
                        RETURNING id""")
                .param("name", fullName)
                .param("title", titleCode)
                .param("slug", slug)
                .query(Integer.class)
                .single();
    }

    static String slugBase(String name) {
        String slug = LessonNames.fold(name).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "profesor" : slug;
    }
}
