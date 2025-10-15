package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.repository.YamlUserRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Builds a normalized "person profile" for the currently logged-in user.
 * If the user is a student (present in YAML students list), it returns FullStudentDetails as well.
 */
@Service
public class PersonProfileService {

    private final YamlUserRepository yamlRepo;

    public PersonProfileService(YamlUserRepository yamlRepo) {
        this.yamlRepo = yamlRepo;
    }

    /** Public API used by controller */
    public PersonProfileDTO getPersonProfileByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        // 1) Determine kind by checking YAML sections
        Optional<Map<String, Object>> studentOpt   = yamlRepo.findStudentByEmail(email);
        Optional<Map<String, Object>> advisorOpt   = yamlRepo.findAdvisorByEmail(email);
        Optional<Map<String, Object>> registrarOpt = yamlRepo.findRegistrarByEmail(email);
        Optional<Map<String, Object>> instrOpt     = yamlRepo.findInstructorByEmail(email);

        // 2) Build DTO
        if (studentOpt.isPresent()) {
            Map<String, Object> s = studentOpt.get();
            return buildStudentDTO(s);
        }

        if (advisorOpt.isPresent()) {
            Map<String, Object> m = advisorOpt.get();
            return basicDTO("advisor", m);
        }

        if (registrarOpt.isPresent()) {
            Map<String, Object> m = registrarOpt.get();
            return basicDTO("registrar", m);
        }

        if (instrOpt.isPresent()) {
            Map<String, Object> m = instrOpt.get();
            return basicDTO("instructor", m);
        }

        // Unknown user (not in YAML) – still return email for frontend to show something
        PersonProfileDTO dto = new PersonProfileDTO();
        dto.kind = "unknown";
        dto.email = email;
        dto.displayName = null;
        dto.sbuId = null;
        return dto;
    }

    // ---------- mapping helpers ----------

    private PersonProfileDTO basicDTO(String kind, Map<String, Object> m) {
        PersonProfileDTO dto = new PersonProfileDTO();
        dto.kind = kind;
        dto.email = getStr(m, "email");
        dto.sbuId = getStr(m, "SBU_ID");
        String first = getStr(m, "first_name");
        String last  = getStr(m, "last_name");
        dto.displayName = joinNames(first, last);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private PersonProfileDTO buildStudentDTO(Map<String, Object> s) {
        PersonProfileDTO dto = new PersonProfileDTO();
        dto.kind = "student";
        dto.email = getStr(s, "email");
        dto.sbuId = getStr(s, "SBU_ID");
        dto.displayName = joinNames(getStr(s, "first_name"), getStr(s, "last_name"));

        // Build FullStudentDetails
        FullStudentDetails details = new FullStudentDetails();
        details.sbuId      = dto.sbuId;
        details.firstName  = getStr(s, "first_name");
        details.lastName   = getStr(s, "last_name");
        details.email      = dto.email;

        Map<String, Object> univEntry = getMap(s, "university_entry");
        if (univEntry != null) {
            details.universityEntry = toTerm(univEntry);
        }

        details.directAdmit = toNullableStr(s.get("direct_admit"));
        details.aoi         = toNullableStr(s.get("AOI"));
        details.college     = toNullableStr(s.get("college"));

        details.majors                    = toStrList(s.get("majors"));
        details.degrees                   = toStrList(s.get("degrees"));
        details.majorRequirementVersions  = toTermList(s.get("major_requirement_versions"));

        details.minors                    = toStrList(s.get("minors"));
        details.minorRequirementVersions  = toTermList(s.get("minor_requirement_versions"));

        details.plannedMajors                     = toStrList(s.get("planned_majors"));
        details.plannedMajorRequirementVersions   = toTermList(s.get("planned_major_requirement_versions"));
        details.plannedDegrees                    = toStrList(s.get("planned_degrees"));

        details.plannedMinors                     = toStrList(s.get("planned_minors"));
        details.plannedMinorRequirementVersions   = toTermList(s.get("planned_minor_requirement_versions"));

        Map<String, Object> grad = getMap(s, "graduation");
        details.graduation = (grad != null ? toTerm(grad) : null);

        dto.studentDetails = details;
        return dto;
    }

    private String joinNames(String first, String last) {
        String a = (first == null ? "" : first.trim());
        String b = (last  == null ? "" : last.trim());
        String joined = (a + " " + b).trim();
        return joined.isEmpty() ? null : joined;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMapList(Object v) {
        if (v instanceof List) {
            return (List<Map<String, Object>>) v;
        }
        return Collections.emptyList();
    }

    private List<String> toStrList(Object v) {
        if (v instanceof List) {
            List<?> raw = (List<?>) v;
            List<String> out = new ArrayList<>();
            for (Object o : raw) {
                out.add(o == null ? null : String.valueOf(o));
            }
            return out;
        }
        return Collections.emptyList();
    }

    private String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String toNullableStr(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private TermRef toTerm(Map<String, Object> m) {
        TermRef t = new TermRef();
        t.semester = getStr(m, "semester");
        Object y = m.get("year");
        if (y instanceof Number) {
            t.year = ((Number) y).intValue();
        } else if (y != null) {
            try { t.year = Integer.parseInt(String.valueOf(y)); } catch (NumberFormatException ignored) { }
        }
        return t;
    }

    private List<TermRef> toTermList(Object v) {
        List<TermRef> out = new ArrayList<>();
        for (Map<String, Object> mm : getMapList(v)) {
            out.add(toTerm(mm));
        }
        return out;
    }

    // ---------- DTOs returned to controller (match frontend expectations) ----------

    public static class PersonProfileDTO {
        public String kind;        // "student" | "advisor" | "registrar" | "instructor" | "unknown"
        public String displayName;
        public String email;
        public String sbuId;
        public FullStudentDetails studentDetails; // present only if kind == "student"
    }

    public static class FullStudentDetails {
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
    }

    public static class TermRef {
        public String semester; // Fall | Spring | SummerI | SummerII
        public Integer year;
    }
}