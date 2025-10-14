package edu.sbu.cse416.sam_system.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import edu.sbu.cse416.sam_system.model.Student;
import edu.sbu.cse416.sam_system.repository.StudentRepository;
import edu.sbu.cse416.sam_system.security.CurrentUser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final StudentRepository studentRepository;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    @Autowired
    public ProfileController(StudentRepository studentRepository, JdbcTemplate jdbc, CurrentUser currentUser) {
        this.studentRepository = studentRepository;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    // --------------------------------------------------------------------------------------------
    // 0) Student search (experiment: unrestricted)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/student")
    @PreAuthorize("permitAll()")
    public Student getStudentByNetId(@RequestParam("netId") String netId) {
        if (netId == null || netId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "netId is required");
        }
        return resolveStudentByNetId(netId.trim());
    }

    @GetMapping("/student-by-email")
    @PreAuthorize("permitAll()")
    public Student getStudentByEmail(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        return studentRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Student not found: " + email));
    }

    // --------------------------------------------------------------------------------------------
    // 1) Basic profile (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Student getProfile() {
        return resolveCurrentStudent();
    }

    // --------------------------------------------------------------------------------------------
    // 2) GPA summary (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/gpa")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getGpaSummary() {
        Student me = resolveCurrentStudent();
        return buildGpaSummary(me.getId());
    }

    // --------------------------------------------------------------------------------------------
    // 3) Current schedule by term (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/schedule")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getSchedule(@RequestParam String term) {
        requireTerm(term);
        Student me = resolveCurrentStudent();
        return querySchedule(me.getId(), term);
    }

    // --------------------------------------------------------------------------------------------
    // 4) Programs (declared & planned) (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/plans")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getPlans() {
        Student me = resolveCurrentStudent();
        return queryPlans(me.getId());
    }

    // --------------------------------------------------------------------------------------------
    // 5) Requirement status: admission (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/requirements/admission")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getAdmissionRequirements() {
        Student me = resolveCurrentStudent();
        return queryRequirements(me.getId(), "ADMISSION");
    }

    // --------------------------------------------------------------------------------------------
    // 6) Requirement status: degree (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/requirements/degree")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getDegreeRequirements() {
        Student me = resolveCurrentStudent();
        return queryRequirements(me.getId(), "DEGREE");
    }

    // --------------------------------------------------------------------------------------------
    // 7) Registration holds (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/holds")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getHolds() {
        Student me = resolveCurrentStudent();
        return queryHolds(me.getId());
    }

    // --------------------------------------------------------------------------------------------
    // 8) Waived time conflicts (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/waivers/time-conflicts")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getTimeConflictWaivers() {
        Student me = resolveCurrentStudent();
        return queryTimeConflictWaivers(me.getId());
    }

    // --------------------------------------------------------------------------------------------
    // 9) Audit log (current user)
    // --------------------------------------------------------------------------------------------
    @GetMapping("/me/audit-log")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getAuditLog() {
        Student me = resolveCurrentStudent();
        return queryAuditLog(me.getId());
    }

    // ============================================================================================
    // A) Student-scoped endpoints (by netId) - mirror of /me versions
    // ============================================================================================
    @GetMapping("/student/{netId}")
    @PreAuthorize("permitAll()")
    public Student getStudentProfile(@PathVariable String netId) {
        return resolveStudentByNetId(netId);
    }

    @GetMapping("/student/{netId}/gpa")
    @PreAuthorize("permitAll()")
    public Map<String, Object> getStudentGpa(@PathVariable String netId) {
        Student s = resolveStudentByNetId(netId);
        return buildGpaSummary(s.getId());
    }

    @GetMapping("/student/{netId}/schedule")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getStudentSchedule(@PathVariable String netId,
                                                        @RequestParam String term) {
        requireTerm(term);
        Student s = resolveStudentByNetId(netId);
        return querySchedule(s.getId(), term);
    }

    @GetMapping("/student/{netId}/plans")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getStudentPlans(@PathVariable String netId) {
        Student s = resolveStudentByNetId(netId);
        return queryPlans(s.getId());
    }

    @GetMapping("/student/{netId}/requirements/admission")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getStudentAdmissionReqs(@PathVariable String netId) {
        Student s = resolveStudentByNetId(netId);
        return queryRequirements(s.getId(), "ADMISSION");
    }

    @GetMapping("/student/{netId}/requirements/degree")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getStudentDegreeReqs(@PathVariable String netId) {
        Student s = resolveStudentByNetId(netId);
        return queryRequirements(s.getId(), "DEGREE");
    }

    @GetMapping("/student/{netId}/holds")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getStudentHolds(@PathVariable String netId) {
        Student s = resolveStudentByNetId(netId);
        return queryHolds(s.getId());
    }

    @GetMapping("/student/{netId}/waivers/time-conflicts")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getStudentTimeConflictWaivers(@PathVariable String netId) {
        Student s = resolveStudentByNetId(netId);
        return queryTimeConflictWaivers(s.getId());
    }

    @GetMapping("/student/{netId}/audit-log")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getStudentAuditLog(@PathVariable String netId) {
        Student s = resolveStudentByNetId(netId);
        return queryAuditLog(s.getId());
    }

    // ============================================================================================
    // Internal query builders (DRY)
    // ============================================================================================
    private Map<String, Object> buildGpaSummary(Long studentId) {
        String termSql =
            "SELECT e.term_code AS term, " +
            "       ROUND(SUM(gp.points * e.credits) / NULLIF(SUM(e.credits),0), 2) AS gpa, " +
            "       SUM(e.credits) AS credits " +
            "FROM enrollments e " +
            "JOIN grade_points gp ON gp.grade = e.grade " +
            "WHERE e.student_id = ? " +
            "  AND e.status = 'GRADED' " +
            "GROUP BY e.term_code " +
            "ORDER BY e.term_code";

        List<Map<String, Object>> terms = safeQueryList(() ->
            jdbc.query(termSql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("term", rs.getString("term"));
                m.put("gpa", readDouble(rs, "gpa"));
                m.put("credits", rs.getInt("credits"));
                return m;
            }, studentId)
        );

        String cumSql =
            "SELECT ROUND(SUM(gp.points * e.credits) / NULLIF(SUM(e.credits),0), 2) AS gpa, " +
            "       SUM(e.credits) AS credits " +
            "FROM enrollments e " +
            "JOIN grade_points gp ON gp.grade = e.grade " +
            "WHERE e.student_id = ? " +
            "  AND e.status = 'GRADED'";

        Map<String, Object> cumulative = safeQueryOne(() ->
            jdbc.query(cumSql, rs -> {
                if (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("gpa", readDouble(rs, "gpa"));
                    m.put("credits", rs.getInt("credits"));
                    return m;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("gpa", null);
                m.put("credits", 0);
                return m;
            }, studentId)
        );

        int cumCredits = (cumulative.get("credits") instanceof Number)
                ? ((Number) cumulative.get("credits")).intValue()
                : 0;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("terms", terms);
        resp.put("cumulative", cumulative);
        resp.put("standing", toStanding(cumCredits));
        return resp;
    }

    private List<Map<String, Object>> querySchedule(Long studentId, String term) {
        String sql =
            "SELECT c.class_id AS classId, " +
            "       c.subject, " +
            "       c.course_num AS courseNum, " +
            "       c.section, " +
            "       c.title, " +
            "       COALESCE(c.schedule, c.meeting) AS schedule, " +
            "       c.location, " +
            "       c.instructor, " +
            "       COALESCE(c.max_credits, c.min_credits) AS credits " +
            "FROM enrollments e " +
            "JOIN classes c ON c.class_id = e.class_id " +
            "WHERE e.student_id = ? " +
            "  AND e.term_code = ? " +
            "  AND e.status = 'ENROLLED' " +
            "ORDER BY c.class_id";

        return safeQueryList(() ->
            jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("classId", rs.getLong("classId"));
                m.put("subject", rs.getString("subject"));
                m.put("courseNum", readInt(rs, "courseNum"));
                m.put("section", rs.getString("section"));
                m.put("title", rs.getString("title"));
                m.put("schedule", rs.getString("schedule"));
                m.put("location", rs.getString("location"));
                m.put("instructor", rs.getString("instructor"));
                m.put("credits", readIntNullable(rs, "credits"));
                return m;
            }, studentId, term)
        );
    }

    private List<Map<String, Object>> queryPlans(Long studentId) {
        String sql =
            "SELECT kind, program_code AS code, program_name AS name, " +
            "       declared, requirement_version AS version " +
            "FROM student_plans " +
            "WHERE student_id = ? " +
            "ORDER BY kind, program_code";

        return safeQueryList(() ->
            jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", rs.getString("kind"));
                m.put("code", rs.getString("code"));
                m.put("name", rs.getString("name"));
                m.put("declared", rs.getBoolean("declared"));
                m.put("version", rs.getString("version"));
                return m;
            }, studentId)
        );
    }

    private List<Map<String, Object>> queryRequirements(Long studentId, String area) {
        String baseSql =
            "SELECT r.id AS rid, r.area, r.program_code AS programCode, r.program_name AS programName, " +
            "       r.req_code AS code, r.title, r.status " +
            "FROM requirement_status r " +
            "WHERE r.student_id = ? AND r.area = ? " +
            "ORDER BY r.program_code, r.req_code";

        List<Map<String, Object>> rows = safeQueryList(() ->
            jdbc.query(baseSql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                long rid = rs.getLong("rid");
                m.put("rid", rid);
                m.put("area", rs.getString("area"));
                m.put("programCode", rs.getString("programCode"));
                m.put("programName", rs.getString("programName"));
                m.put("code", rs.getString("code"));
                m.put("title", rs.getString("title"));
                m.put("status", rs.getString("status"));
                m.put("usingClasses", new ArrayList<Map<String, Object>>());
                return m;
            }, studentId, area)
        );

        String usedSql =
            "SELECT subject, course_num AS courseNum, term_code AS term, grade " +
            "FROM requirement_used_classes " +
            "WHERE requirement_id = ? " +
            "ORDER BY term_code, subject, course_num";

        for (Map<String, Object> r : rows) {
            Long rid = (Long) r.get("rid");
            List<Map<String, Object>> used = safeQueryList(() ->
                jdbc.query(usedSql, (rs, rowNum) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("subject", rs.getString("subject"));
                    m.put("courseNum", readInt(rs, "courseNum"));
                    m.put("term", rs.getString("term"));
                    m.put("grade", rs.getString("grade"));
                    return m;
                }, rid)
            );
            r.remove("rid");
            r.put("usingClasses", used);
        }
        return rows;
    }

    private List<Map<String, Object>> queryHolds(Long studentId) {
        String sql =
            "SELECT hold_code AS code, title, placed_at AS placedAt, placed_by AS placedBy " +
            "FROM registration_holds " +
            "WHERE student_id = ? " +
            "ORDER BY placed_at DESC";

        return safeQueryList(() ->
            jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", rs.getString("code"));
                m.put("title", rs.getString("title"));
                m.put("placedAt", rs.getString("placedAt"));
                m.put("placedBy", rs.getString("placedBy"));
                return m;
            }, studentId)
        );
    }

    private List<Map<String, Object>> queryTimeConflictWaivers(Long studentId) {
        String sql =
            "SELECT class_a AS classA, class_b AS classB, term_code AS term, " +
            "       waived_by AS waivedBy, waived_at AS waivedAt " +
            "FROM time_conflict_waivers " +
            "WHERE student_id = ? " +
            "ORDER BY waived_at DESC";

        return safeQueryList(() ->
            jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("classA", rs.getString("classA"));
                m.put("classB", rs.getString("classB"));
                m.put("term", rs.getString("term"));
                m.put("waivedBy", rs.getString("waivedBy"));
                m.put("waivedAt", rs.getString("waivedAt"));
                return m;
            }, studentId)
        );
    }

    private List<Map<String, Object>> queryAuditLog(Long studentId) {
        String sql =
            "SELECT id, ts AS timestamp, actor_role AS actorRole, actor_display AS actorDisplay, " +
            "       action, details " +
            "FROM audit_log " +
            "WHERE student_id = ? " +
            "ORDER BY ts DESC, id DESC";

        return safeQueryList(() ->
            jdbc.query(sql, (rs, rowNum) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("timestamp", rs.getString("timestamp"));
                m.put("actorRole", rs.getString("actorRole"));
                m.put("actorDisplay", rs.getString("actorDisplay"));
                m.put("action", rs.getString("action"));
                m.put("details", rs.getString("details"));
                return m;
            }, studentId)
        );
    }

    // ============================================================================================
    // Helpers
    // ============================================================================================
    private void requireTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "term is required, e.g., 2025-Fall");
        }
    }

    private Student resolveCurrentStudent() {
        String netId = currentUser.getNetId();
        if (netId == null || netId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No logged-in user");
        }
        return resolveStudentByNetId(netId);
    }

    private Student resolveStudentByNetId(String netId) {
        return studentRepository.findByNetIdIgnoreCase(netId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Student not found: " + netId));
    }

    private static String toStanding(int credits) {
        if (credits < 24) return "U1";
        if (credits < 57) return "U2";
        if (credits < 85) return "U3";
        return "U4";
    }

    // ---------- JDBC helpers ----------
    private static Double readDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer readInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        if (rs.wasNull()) return 0;
        return v;
    }

    private static Integer readIntNullable(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    // ---------- Safe wrappers ----------
    private interface SqlSupplier<T> { T get() throws Exception; }

    private <T> T safeQueryOne(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            if (ex instanceof ResponseStatusException) throw (ResponseStatusException) ex;
            return (T) new LinkedHashMap<String, Object>();
        }
    }

    private List<Map<String, Object>> safeQueryList(SqlSupplier<List<Map<String, Object>>> supplier) {
        try {
            return supplier.get();
        } catch (Exception ex) {
            if (ex instanceof ResponseStatusException) throw (ResponseStatusException) ex;
            return Collections.emptyList();
        }
    }
}