package edu.sbu.cse416.sam_system.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import edu.sbu.cse416.sam_system.security.CurrentUser;
import edu.sbu.cse416.sam_system.service.PersonProfileService; 
import edu.sbu.cse416.sam_system.service.PersonProfileService.TermRef; // Import TermRef from PersonProfileService for use
import edu.sbu.cse416.sam_system.service.SystemDateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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
    private final SystemDateService systemDateService;
    private final DegreeRequirementRepository degreeRequirementRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ProfileController(UserRepository userRepository, JdbcTemplate jdbc, CurrentUser currentUser, 
                           PersonProfileService personProfileService, SystemDateService systemDateService,
                           DegreeRequirementRepository degreeRequirementRepository) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.personProfileService = personProfileService;
        this.systemDateService = systemDateService;
        this.degreeRequirementRepository = degreeRequirementRepository;
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

    @GetMapping("/me/requirements/admission")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getAdmissionReqsMe() {
        User u = resolveUserForMe();
        if (!u.isStudent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a student");
        }
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryRequirements(u.getStudentId(), "ADMISSION");
    }

    @GetMapping("/me/requirements/degree")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getDegreeReqsMe() {
        User u = resolveUserForMe();
        if (!u.isStudent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a student");
        }
        if (u.getStudentId() == null) return Collections.emptyList();
        return queryRequirements(u.getStudentId(), "DEGREE");
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
        // Parse term from "2026-Spring" format to semester and year
        String[] termParts = term.split("-");
        if (termParts.length != 2) {
            // Invalid term format, return empty list
            return Collections.emptyList();
        }
        
        Integer year;
        String semester;
        try {
            year = Integer.parseInt(termParts[0]);
            semester = termParts[1]; // "Spring", "Fall", etc.
        } catch (NumberFormatException e) {
            // Invalid year format, return empty list
            return Collections.emptyList();
        }
        
        // Determine if this is the current term or a past term
        // Check if there are any ENROLLED classes in ClassRoster for this term
        // If yes, it's the current term - show only ENROLLED classes
        // If no, it's a past term - show classes from UserClasses (historical data)
        String checkCurrentTermSql =
            "SELECT COUNT(*) > 0 AS hasEnrolled " +
            "FROM class_roster cr " +
            "JOIN classes c ON c.class_id = cr.class_id " +
            "JOIN users u ON u.user_id = cr.user_id " +
            "WHERE u.student_id = ? " +
            "  AND c.semester = ? " +
            "  AND c.academic_year = ? " +
            "  AND cr.status = 'ENROLLED'";
        
        Boolean isCurrentTerm = safeQueryOne(() ->
            jdbc.query(checkCurrentTermSql, rs -> {
                if (rs.next()) {
                    return rs.getBoolean("hasEnrolled");
                }
                return false;
            }, studentId, semester, year)
        );
        
        if (isCurrentTerm != null && isCurrentTerm) {
            // Current term: Show only ENROLLED classes from ClassRoster
            String rosterSql =
                "SELECT c.class_id AS classId, " +
                "       c.department AS subject, " +
                "       c.course_num AS courseNum, " +
                "       c.section, " +
                "       c.title, " +
                "       COALESCE(c.schedule, c.lec_schedule) AS schedule, " +
                "       c.location, " +
                "       c.instructor, " +
                "       COALESCE(c.max_credits, c.min_credits) AS credits " +
                "FROM class_roster cr " +
                "JOIN classes c ON c.class_id = cr.class_id " +
                "JOIN users u ON u.user_id = cr.user_id " +
                "WHERE u.student_id = ? " +
                "  AND c.semester = ? " +
                "  AND c.academic_year = ? " +
                "  AND cr.status = 'ENROLLED' " +
                "ORDER BY c.class_id";
            
            return safeQueryList(() ->
                jdbc.query(rosterSql, (rs, rowNum) -> {
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
                }, studentId, semester, year)
            );
        } else {
            // Past term: Show classes from UserClasses (historical data)
            String userClassesSql =
                "SELECT DISTINCT " +
                "       COALESCE(c.class_id, uc.class_id) AS classId, " +
                "       COALESCE(c.department, uc.department) AS subject, " +
                "       COALESCE(c.course_num, uc.course_num) AS courseNum, " +
                "       COALESCE(c.section, uc.section) AS section, " +
                "       COALESCE(c.title, CONCAT(uc.department, ' ', uc.course_num)) AS title, " +
                "       COALESCE(c.schedule, c.lec_schedule, '') AS schedule, " +
                "       COALESCE(c.location, '') AS location, " +
                "       COALESCE(c.instructor, '') AS instructor, " +
                "       COALESCE(c.max_credits, c.min_credits, uc.credits) AS credits " +
                "FROM user_classes uc " +
                "JOIN users u ON u.user_id = uc.user_id " +
                "LEFT JOIN classes c ON c.class_id = uc.class_id " +
                "WHERE u.student_id = ? " +
                "  AND uc.semester = ? " +
                "  AND uc.year = ? " +
                "ORDER BY classId";
            
            return safeQueryList(() ->
                jdbc.query(userClassesSql, (rs, rowNum) -> {
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
                }, studentId, semester, year)
            );
        }
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
        System.out.println("DEBUG: queryRequirements called for studentId: " + studentId + ", area: " + area);
        
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

        System.out.println("DEBUG: Found " + rows.size() + " rows from requirement_status table");

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
        
        // If no rows found and area is ADMISSION, try to generate from AOI
        if (rows.isEmpty() && "ADMISSION".equals(area)) {
            System.out.println("DEBUG: No rows found, generating from AOI");
            rows = generateAdmissionRequirementsFromAOI(studentId);
            System.out.println("DEBUG: Generated " + rows.size() + " requirements from AOI");
        }
        
        return rows;
    }
    
    /**
     * Generate admission requirements dynamically from student's AOI
     * if requirement_status table doesn't have entries
     */
    private List<Map<String, Object>> generateAdmissionRequirementsFromAOI(String studentId) {
        List<Map<String, Object>> requirements = new ArrayList<>();
        
        try {
            System.out.println("DEBUG: generateAdmissionRequirementsFromAOI called for studentId: " + studentId);
            
            // Get student user
            User student = userRepository.findByStudentId(studentId)
                .orElse(null);
            
            if (student == null) {
                System.out.println("DEBUG: Student not found for studentId: " + studentId);
                return requirements;
            }
            
            if (student.getAoi() == null || student.getAoi().trim().isEmpty()) {
                System.out.println("DEBUG: Student has no AOI");
                return requirements;
            }
            
            String aoi = student.getAoi().trim();
            System.out.println("DEBUG: Student AOI: " + aoi);
            
            // Find degree requirement for AOI (prefer BS, fallback to BA)
            List<DegreeRequirement> aoiRequirements = degreeRequirementRepository
                .findBySubjectCodeAndActive(aoi);
            
            if (aoiRequirements.isEmpty()) {
                System.out.println("DEBUG: No degree requirements found for AOI: " + aoi);
                return requirements;
            }
            
            System.out.println("DEBUG: Found " + aoiRequirements.size() + " degree requirements for AOI: " + aoi);
            
            // Prefer BS, then BA
            DegreeRequirement requirement = aoiRequirements.stream()
                .filter(r -> r.getDegreeType() == DegreeType.BS)
                .findFirst()
                .orElse(aoiRequirements.stream()
                    .filter(r -> r.getDegreeType() == DegreeType.BA)
                    .findFirst()
                    .orElse(aoiRequirements.get(0)));
            
            System.out.println("DEBUG: Using degree requirement: " + requirement.getSubjectCode() + " " + requirement.getDegreeType());
            
            // Parse admission requirements from JSON
            String admissionJson = requirement.getAdmissionRequirements();
            if (admissionJson == null || admissionJson.trim().isEmpty()) {
                System.out.println("DEBUG: No admission requirements JSON found");
                return requirements;
            }
            
            System.out.println("DEBUG: Admission requirements JSON: " + admissionJson.substring(0, Math.min(200, admissionJson.length())));
            
            Map<String, Object> admissionData = objectMapper.readValue(
                admissionJson, new TypeReference<Map<String, Object>>() {});
            
            // Get core courses from admission requirements
            List<Map<String, Object>> coreCourses = null;
            if (admissionData.containsKey("core_courses")) {
                Object coreCoursesObj = admissionData.get("core_courses");
                if (coreCoursesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> temp = (List<Map<String, Object>>) coreCoursesObj;
                    coreCourses = temp;
                }
            }
            
            if (coreCourses == null || coreCourses.isEmpty()) {
                System.out.println("DEBUG: No core courses found in admission requirements");
                return requirements;
            }
            
            System.out.println("DEBUG: Found " + coreCourses.size() + " core courses");
            
            // Get student's enrolled classes
            String classesSql = 
                "SELECT uc.department AS subject, uc.course_num AS courseNum, " +
                "       CONCAT(uc.year, '-', uc.semester) AS term, " +
                "       uc.grade " +
                "FROM user_classes uc " +
                "JOIN users u ON u.user_id = uc.user_id " +
                "WHERE u.student_id = ? " +
                "ORDER BY uc.year DESC, uc.semester DESC";
            
            List<Map<String, Object>> studentClasses = safeQueryList(() ->
                jdbc.query(classesSql, (rs, rowNum) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("subject", rs.getString("subject"));
                    m.put("courseNum", readInt(rs, "courseNum"));
                    m.put("term", rs.getString("term"));
                    m.put("grade", rs.getString("grade"));
                    return m;
                }, studentId)
            );
            
            System.out.println("DEBUG: Found " + studentClasses.size() + " student classes");
            for (Map<String, Object> sc : studentClasses) {
                System.out.println("DEBUG: Student class: " + sc.get("subject") + " " + sc.get("courseNum") + " grade: " + sc.get("grade"));
            }
            
            // Create requirement status for each core course
            String programCode = aoi;
            String programName = aoi + " " + requirement.getDegreeType();
            
            for (Map<String, Object> coreCourse : coreCourses) {
                String courseCode = (String) coreCourse.get("code");
                if (courseCode == null) {
                    System.out.println("DEBUG: Core course has no code field");
                    continue;
                }
                
                System.out.println("DEBUG: Processing core course: " + courseCode);
                
                // Parse course code (e.g., "CSE 114")
                String[] parts = courseCode.split("\\s+");
                if (parts.length != 2) {
                    System.out.println("DEBUG: Invalid course code format: " + courseCode);
                    continue;
                }
                
                String subject = parts[0];
                int courseNum = Integer.parseInt(parts[1]);
                
                // Find matching class in student's classes
                List<Map<String, Object>> matchingClasses = new ArrayList<>();
                for (Map<String, Object> studentClass : studentClasses) {
                    String classSubject = (String) studentClass.get("subject");
                    Integer classCourseNum = (Integer) studentClass.get("courseNum");
                    
                    if (subject.equals(classSubject) && courseNum == classCourseNum) {
                        matchingClasses.add(studentClass);
                        System.out.println("DEBUG: Matched class: " + classSubject + " " + classCourseNum);
                    }
                }
                
                // Determine status
                String status = "unsatisfied";
                if (!matchingClasses.isEmpty()) {
                    // Check if any class has a grade (completed) or grade is null (in progress)
                    boolean hasGrade = matchingClasses.stream()
                        .anyMatch(c -> c.get("grade") != null);
                    if (hasGrade) {
                        status = "satisfied";
                    } else {
                        status = "pending"; // In progress
                    }
                }
                
                System.out.println("DEBUG: Requirement status for " + courseCode + ": " + status);
                
                Map<String, Object> req = new LinkedHashMap<>();
                req.put("area", "ADMISSION");
                req.put("programCode", programCode);
                req.put("programName", programName);
                req.put("code", courseCode);
                req.put("title", courseCode);
                req.put("status", status);
                req.put("usingClasses", matchingClasses);
                
                requirements.add(req);
            }
            
            System.out.println("DEBUG: Generated " + requirements.size() + " admission requirements");
            
        } catch (Exception e) {
            System.err.println("Error generating admission requirements from AOI: " + e.getMessage());
            e.printStackTrace();
        }
        
        return requirements;
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