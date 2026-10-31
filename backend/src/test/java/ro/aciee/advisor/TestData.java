package ro.aciee.advisor;

import org.springframework.jdbc.core.simple.JdbcClient;

public class TestData {

    public static int currentGroup(JdbcClient jdbc, String code, int maxSize) {
        int facultyId = insert(jdbc, "INSERT INTO core.faculty (code, name_ro) VALUES ('%s', 'Test') RETURNING id".formatted(code));
        int specializationId = insert(jdbc, """
                INSERT INTO core.specialization (faculty_id, code, name_ro, degree, duration_years)
                VALUES (%d, '%s', 'Calculatoare', 'BSc', 4) RETURNING id""".formatted(facultyId, code));
        int yearId = jdbc.sql("""
                        INSERT INTO core.academic_year (during)
                        VALUES (daterange(current_date - 30, current_date + 300)) ON CONFLICT DO NOTHING RETURNING id""")
                .query(Integer.class)
                .optional()
                .orElseGet(() -> insert(jdbc, "SELECT id FROM core.academic_year WHERE during @> current_date"));
        int planId = insert(jdbc, "INSERT INTO core.study_plan (specialization_id, intake) VALUES (%d, '[2020,2030)') RETURNING id"
                .formatted(specializationId));
        return insert(jdbc, """
                INSERT INTO core.study_group (plan_id, academic_year_id, study_year, code, max_size)
                VALUES (%d, %d, 3, '%s', %d) RETURNING id""".formatted(planId, yearId, code, maxSize));
    }

    public static int account(JdbcClient jdbc, String localPart) {
        return insert(jdbc, "INSERT INTO core.user_account (email, password_hash) VALUES ('%s@student.ugal.ro', 'x') RETURNING id"
                .formatted(localPart));
    }

    public static int insert(JdbcClient jdbc, String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
