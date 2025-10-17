package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.repository.UserRepository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Builds a normalized "person profile" for the currently logged-in user.
 * MODIFIED to use DB UserRepository instead of YAML to fetch profile details 
 * including department and office information.
 */
@Service
public class PersonProfileService {

    private final UserRepository userRepository; 

    @Autowired
    public PersonProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Public API used by controller */
    public PersonProfileDTO getPersonProfileByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        // 1) DB에서 사용자 조회 
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email.trim());

        if (userOpt.isEmpty()) {
            PersonProfileDTO dto = new PersonProfileDTO();
            dto.kind = "unknown";
            dto.email = email;
            dto.displayName = null;
            dto.sbuId = null;
            return dto;
        }

        User u = userOpt.get();
        String kind = u.getRole().name().toLowerCase();

        // 2) DTO 빌드
        PersonProfileDTO dto = new PersonProfileDTO();
        dto.kind = kind;
        dto.email = u.getEmail();
        dto.sbuId = u.getStudentId();
        dto.firstName = u.getFirstName();
        dto.lastName = u.getLastName();
        dto.displayName = joinNames(u.getFirstName(), u.getLastName());

        // ******************** users 테이블의 모든 필드 매핑 ********************
        dto.department = u.getDepartment();
        dto.officeHours = u.getOfficeHours();
        dto.officeLocation = u.getOfficeLocation();
        dto.academicUnit = u.getAcademicUnit(); 
        dto.unitLevel = u.getLevel(); 
        
        // Student/General fields
        dto.major = u.getMajor();           
        dto.gpa = u.getGpa();               
        dto.graduationYear = u.getGraduationYear(); 
        
        // Class Standing: GpaSummary의 Standing과 동일한 정보를 제공 (현재는 임시값)
        if ("student".equals(kind)) {
             // 임시 Class Standing 계산 (실제는 credits 기반이지만, users 테이블의 gpa를 사용하여 임시로 채움)
             dto.classStanding = toStanding(u.getGpa() != null ? u.getGpa().doubleValue() : 0.0);
        } else {
             dto.classStanding = null;
        }
        // ******************************************************************

        if ("student".equals(kind)) {
            dto.studentDetails = buildStudentDetails(u);
            dto.sbuId = u.getStudentId();
        }

        return dto;
    }

    // ---------- mapping helpers ----------

    private FullStudentDetails buildStudentDetails(User u) {
        FullStudentDetails details = new FullStudentDetails();
        details.sbuId      = u.getStudentId();
        details.firstName  = u.getFirstName();
        details.lastName   = u.getLastName();
        details.email      = u.getEmail();

        // DB의 JSON String 필드들을 파싱해야 합니다. (임시로 null/빈 리스트)
        details.universityEntry = parseTermRef(u.getUniversityEntry());
        details.directAdmit = u.getDirectAdmit();
        details.aoi = u.getAoi();
        details.college = u.getCollege();

        details.majors = parseStrList(u.getMajors());
        details.degrees = parseStrList(u.getDegrees());
        details.majorRequirementVersions = parseTermList(u.getMajorRequirementVersions());
        details.minors = parseStrList(u.getMinors());
        details.minorRequirementVersions = parseTermList(u.getMinorRequirementVersions());
        details.plannedMajors = parseStrList(u.getPlannedMajors());
        details.plannedMajorRequirementVersions = parseTermList(u.getPlannedMajorRequirementVersions());
        details.plannedDegrees = parseStrList(u.getPlannedDegrees());
        details.plannedMinors = parseStrList(u.getPlannedMinors());
        details.plannedMinorRequirementVersions = parseTermList(u.getPlannedMinorRequirementVersions());

        details.graduation = parseTermRef(u.getGraduation());

        return details;
    }
    
    // GPA 값에 기반한 임시 Standing 계산 헬퍼 (실제는 Credits를 사용해야 함)
    private String toStanding(Double gpa) {
        if (gpa == null) return null;
        if (gpa >= 3.5) return "Senior (Excellent)";
        if (gpa >= 3.0) return "Junior (Good)";
        if (gpa >= 2.5) return "Sophomore";
        if (gpa >= 2.0) return "Freshman";
        return null;
    }

    // 🚨 수정됨: public static으로 변경
    public static TermRef parseTermRef(String jsonString) {
        // 실제 JSON 파싱 구현 필요
        return null; 
    }
    
    // 🚨 수정됨: public static으로 변경
    public static List<String> parseStrList(String jsonString) {
        // 실제 JSON 파싱 구현 필요 (JSON Array String -> List<String>)
        // 임시로 JSON이 아닌 쉼표 구분 문자열이라고 가정하고 처리
        if (jsonString == null || jsonString.isBlank() || jsonString.equals("[]")) {
            return Collections.emptyList();
        }
        // 실제 JSON 파싱 코드가 여기에 들어갈 것입니다.
        return Arrays.asList(jsonString.replaceAll("[\\[\\]\"]", "").split(",")); // 임시 처리
    }
    
    // 🚨 수정됨: public static으로 변경
    public static List<TermRef> parseTermList(String jsonString) {
        // 실제 JSON 파싱 구현 필요
        return Collections.emptyList(); 
    }


    private String joinNames(String first, String last) {
        String a = (first == null ? "" : first.trim());
        String b = (last  == null ? "" : last.trim());
        String joined = (a + " " + b).trim();
        return joined.isEmpty() ? null : joined;
    }


    // ---------- DTOs returned to controller (match frontend expectations) ----------

    public static class PersonProfileDTO {
        public String kind;        // "student" | "advisor" | "registrar" | "instructor" | "unknown"
        public String displayName;
        public String email;
        public String sbuId;
        public String firstName;
        public String lastName;
        
        // ******************** 추가된 DTO 필드 ********************
        public String department;
        public String officeHours;
        public String officeLocation;
        public String academicUnit;
        public String unitLevel;
        public String major;
        public Double gpa;
        public Integer graduationYear;
        public String classStanding;
        // ******************************************************
        
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