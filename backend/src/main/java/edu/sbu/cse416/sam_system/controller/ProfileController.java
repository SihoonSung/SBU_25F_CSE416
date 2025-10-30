package edu.sbu.cse416.sam_system.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.security.CurrentUser;
import edu.sbu.cse416.sam_system.service.PersonProfileService; 
import edu.sbu.cse416.sam_system.service.PersonProfileService.TermRef; // Import TermRef from PersonProfileService for use

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * TP: Used cursor to implement REST controller for user profile management.
 */
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final PersonProfileService personProfileService; 

    @Autowired
    public ProfileController(UserRepository userRepository, JdbcTemplate jdbc, CurrentUser currentUser, PersonProfileService personProfileService) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.personProfileService = personProfileService; 
    }

    /* ======================================================================
     * DTOs
     * ====================================================================== */
    public static class StudentProfileDto {
        public Long id;
        public String studentId;
        public String name;
        public String major;
        public String email;
        public String status;     
        public String entryYear;  

        public static StudentProfileDto fromUser(User u) {
            StudentProfileDto d = new StudentProfileDto();
            d.id = u.getUserId();
            d.studentId = u.getStudentId();
            d.name = buildName(u.getFirstName(), u.getLastName());
            d.major = nvl(u.getMajor(), "-");
            d.email = nvl(u.getEmail(), "-");
            Boolean active = u.getIsActive();
            d.status = (active != null && active) ? "Active" : "Inactive";
            d.entryYear = "-";
            return d;
        }
    }

    public static class FullStudentDetailsDto {
        public String sbuId;
        public String firstName;
        public String lastName;
        public String email;
        public TermRef universityEntry;
        public String directAdmit;      
        public String aoi;              
        public String college;          
        public List<String> majors = new ArrayList<>();
        public List<String> degrees = new ArrayList<>();
        public List<TermRef> majorRequirementVersions = new ArrayList<>();
        public List<String> minors = new ArrayList<>();
        public List<TermRef> minorRequirementVersions = new ArrayList<>();
        public List<String> plannedMajors = new ArrayList<>();
        public List<TermRef> plannedMajorRequirementVersions = new ArrayList<>();
        public List<String> plannedDegrees = new ArrayList<>();
        public List<String> plannedMinors = new ArrayList<>();
        public List<TermRef> plannedMinorRequirementVersions = new ArrayList<>();
        public TermRef graduation;

        public static FullStudentDetailsDto fromUser(User u) {
            FullStudentDetailsDto d = new FullStudentDetailsDto();
            d.sbuId = u.getStudentId();
            d.firstName = u.getFirstName();
            d.lastName = u.getLastName();
            d.email = u.getEmail();
            d.college = u.getCollege();

            // Core Details
            d.universityEntry = PersonProfileService.parseTermRef(u.getUniversityEntry());
            d.directAdmit = u.getDirectAdmit();
            d.aoi = u.getAoi();
            
            // Declared Programs: Parse JSON string fields from DB and add to DTO List fields.
            
            // Fallback: If majors array field is empty, use single major field.
            if (u.getMajor() != null && !u.getMajor().isBlank() && PersonProfileService.parseStrList(u.getMajors()).isEmpty()) {
                 d.majors.add(u.getMajor()); 
            }
            d.majors.addAll(PersonProfileService.parseStrList(u.getMajors()));
            d.degrees.addAll(PersonProfileService.parseStrList(u.getDegrees()));
            d.majorRequirementVersions.addAll(PersonProfileService.parseTermList(u.getMajorRequirementVersions()));
            d.minors.addAll(PersonProfileService.parseStrList(u.getMinors()));
            d.minorRequirementVersions.addAll(PersonProfileService.parseTermList(u.getMinorRequirementVersions()));

            // Planned Programs
            d.plannedMajors.addAll(PersonProfileService.parseStrList(u.getPlannedMajors()));
            d.plannedMajorRequirementVersions.addAll(PersonProfileService.parseTermList(u.getPlannedMajorRequirementVersions()));
            d.plannedDegrees.addAll(PersonProfileService.parseStrList(u.getPlannedDegrees()));
            d.plannedMinors.addAll(PersonProfileService.parseStrList(u.getPlannedMinors()));
            d.plannedMinorRequirementVersions.addAll(PersonProfileService.parseTermList(u.getPlannedMinorRequirementVersions()));

            // Graduation
            if (u.getGraduation() != null) {
                d.graduation = PersonProfileService.parseTermRef(u.getGraduation());
            } else if (u.getGraduationYear() != null) {
                TermRef g = new TermRef();
                g.year = u.getGraduationYear();
                g.semester = null;
                d.graduation = g;
            }
            
            return d;
        }
    }

    /* ======================================================================
     * Helpers - common
     * ====================================================================== */
    private static String buildName(String fn, String ln) {
        String a = fn == null ? "" : fn.trim();
        String b = ln == null ? "" : ln.trim();
        String both = (a + " " + b).trim();
        return both.isEmpty() ? "-" : both;
    }

    private static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private void requireTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "term is required, e.g., 2025-Fall");
        }
    }
    
    /* ======================================================================
     * Auth Endpoint (Temporary replacement for missing AuthController)
     * ====================================================================== */
    // 🚨 Modified path to implement /api/auth/me endpoint (frontend also needs modification)
    @GetMapping("/auth-info") // New path: /api/profile/auth-info
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> getAuthMe() {
        User u = resolveUserForMe();
        
        // Create roles array based on DB role
        List<String> roles = Arrays.asList(u.getRole().name()); 

        Map<String, Object> response = new HashMap<>();
        // Match frontend CurrentUserInfo type.
        response.put("netId", u.getEmail()); 
        response.put("roles", roles); 
        
        // Added to fill Role Type (PersonBasic.role) field
        response.put("role", u.getRole().name().toLowerCase());
        
        return response;
    }

    /* ======================================================================
     * Current user profile (users only)
     * ====================================================================== */
    
    // ******************** Endpoint: /api/profile/person/me ********************
    @GetMapping("/person/me")
    @PreAuthorize("isAuthenticated()")
    public PersonProfileService.PersonProfileDTO getPersonProfileMe() {
        String email = currentUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No logged-in user email");
        }
        return personProfileService.getPersonProfileByEmail(email);
    }
    // *********************************************************************************

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public StudentProfileDto getProfileMe() {
        User u = resolveUserForMe();
        if (!u.isStudent()) {
             throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a student");
        }
        return StudentProfileDto.fromUser(u);
    }

    @GetMapping("/me/details")
    @PreAuthorize("isAuthenticated()")
    public FullStudentDetailsDto getProfileMeDetails() {
        User u = resolveUserForMe();
         if (!u.isStudent()) {
             throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a student");
        }
        return FullStudentDetailsDto.fromUser(u);
    }

    /* ======================================================================
     * Lookup by studentId (users only) - used by the frontend
     * ====================================================================== */
    @GetMapping("/student-id/{sid}")
    @PreAuthorize("permitAll()")
    public StudentProfileDto getProfileByStudentId(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        return StudentProfileDto.fromUser(u);
    }

    @GetMapping("/student-id/{sid}/details")
    @PreAuthorize("permitAll()")
    public FullStudentDetailsDto getDetailsByStudentId(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        return FullStudentDetailsDto.fromUser(u);
    }

    /* ======================================================================
     * Legacy: lookup by email (users only)
     * ====================================================================== */
    @GetMapping("/student-by-email")
    @PreAuthorize("permitAll()")
    public StudentProfileDto getProfileByEmail(@RequestParam("email") String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        User u = userRepository.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by email: " + email));
        return StudentProfileDto.fromUser(u);
    }

    /* ======================================================================
     * Data endpoints (GPA, schedule, etc.)
     * Using User.studentId (String) directly in queries.
     * ====================================================================== */
    @GetMapping("/student-id/{sid}/gpa")
    @PreAuthorize("permitAll()")
    public Map<String, Object> getGpaBySid(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return emptyGpa();
        return buildGpaSummary(u.getStudentId());
    }

    @GetMapping("/student-id/{sid}/schedule")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getScheduleBySid(@PathVariable String sid, @RequestParam String term) {
        requireTerm(term);
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return Collections.emptyList();
        return querySchedule(u.getStudentId(), term);
    }

    @GetMapping("/student-id/{sid}/plans")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getPlansBySid(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryPlans(u.getStudentId());
    }

    @GetMapping("/student-id/{sid}/requirements/admission")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getAdmissionReqsBySid(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryRequirements(u.getStudentId(), "ADMISSION");
    }

    @GetMapping("/student-id/{sid}/requirements/degree")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getDegreeReqsBySid(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryRequirements(u.getStudentId(), "DEGREE");
    }

    @GetMapping("/student-id/{sid}/holds")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getHoldsBySid(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryHolds(u.getStudentId());
    }

    @GetMapping("/student-id/{sid}/waivers/time-conflicts")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getWaiversBySid(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryTimeConflictWaivers(u.getStudentId());
    }

    @GetMapping("/student-id/{sid}/audit-log")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getAuditLogBySid(@PathVariable String sid) {
        User u = resolveUserByStudentId(sid);
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryAuditLog(u.getStudentId());
    }

    /* ======================================================================
     * Optional extras for frontend compatibility
     * ====================================================================== */
    @GetMapping("/student-id/{sid}/transfers")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getTransfersBySid(@PathVariable String sid) {
        return Collections.emptyList();
    }

    @GetMapping("/student-id/{sid}/classes")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getClassesBySid(@PathVariable String sid) {
        return Collections.emptyList();
    }

    @GetMapping("/student-id/{sid}/course-plan")
    @PreAuthorize("permitAll()")
    public List<Map<String, Object>> getCoursePlanBySid(@PathVariable String sid) {
        return Collections.emptyList();
    }

    /* ======================================================================
     * GPA summary
     * ====================================================================== */
    private Map<String, Object> buildGpaSummary(String studentId) {
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
        // Fallback: if no graded enrollments found, derive credits from user_classes (graded only)
        if (cumCredits == 0) {
            String fallbackSql =
                "SELECT COALESCE(SUM(uc.credits), 0) AS credits " +
                "FROM user_classes uc " +
                "JOIN users u ON u.user_id = uc.user_id " +
                "WHERE u.student_id = ? " +
                "  AND uc.grade IS NOT NULL";

            Integer fallbackCredits = safeQueryOne(() ->
                jdbc.query(fallbackSql, rs -> {
                    if (rs.next()) {
                        return rs.getInt("credits");
                    }
                    return 0;
                }, studentId)
            );

            // Update cumulative credits for UI and standing calculation if fallback yields > 0
            if (fallbackCredits != null && fallbackCredits > 0) {
                Map<String, Object> adjustedCumulative = new LinkedHashMap<>(cumulative);
                adjustedCumulative.put("credits", fallbackCredits);
                resp.put("cumulative", adjustedCumulative);
                resp.put("standing", toStanding(fallbackCredits));
                return resp;
            }
        }

        resp.put("cumulative", cumulative);
        resp.put("standing", toStanding(cumCredits));
        return resp;
    }

    private Map<String, Object> emptyGpa() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("terms", Collections.emptyList());
        Map<String, Object> cumulative = new LinkedHashMap<>();
        cumulative.put("gpa", null);
        cumulative.put("credits", 0);
        resp.put("cumulative", cumulative);
        resp.put("standing", "U1");
        return resp;
    }

    /* ======================================================================
     * JDBC queries for other data
     * ====================================================================== */
    private List<Map<String, Object>> querySchedule(String studentId, String term) {
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

    private List<Map<String, Object>> queryPlans(String studentId) {
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

    private List<Map<String, Object>> queryRequirements(String studentId, String area) {
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

    private List<Map<String, Object>> queryHolds(String studentId) {
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

    private List<Map<String, Object>> queryTimeConflictWaivers(String studentId) {
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

    private List<Map<String, Object>> queryAuditLog(String studentId) {
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

    /* ======================================================================
     * Auth/user resolvers (users only)
     * ====================================================================== */
    private User resolveUserForMe() {
        String email = currentUser.getEmail();
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by email: " + email));
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No logged-in user");
    }

    private User resolveUserByStudentId(String sid) {
        if (sid == null || sid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required");
        }
        return userRepository.findByStudentId(sid.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found by studentId: " + sid));
    }

    /* ======================================================================
     * Utils
     * ====================================================================== */
    private static String toStanding(int credits) {
        if (credits < 24) return "U1";
        if (credits < 57) return "U2";
        if (credits < 85) return "U3";
        return "U4";
    }

    private static Double readDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer readInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? 0 : v;
    }

    private static Integer readIntNullable(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private interface SqlSupplier<T> { T get() throws Exception; }

    @SuppressWarnings("unchecked")
    private <T> T safeQueryOne(SqlSupplier<T> supplier) {
        try { return supplier.get(); }
        catch (Exception ex) {
            if (ex instanceof ResponseStatusException) throw (ResponseStatusException) ex;
            return (T) new LinkedHashMap<String, Object>();
        }
    }

    private List<Map<String, Object>> safeQueryList(SqlSupplier<List<Map<String, Object>>> supplier) {
        try { return supplier.get(); }
        catch (Exception ex) {
            if (ex instanceof ResponseStatusException) throw (ResponseStatusException) ex;
            return Collections.emptyList();
        }
    }
}