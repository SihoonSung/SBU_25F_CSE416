package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.repository.UserRepository; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * TP: Used cursor to implement service for building normalized person profile.
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

        // 1) Query user from database 
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

        // 2) Build DTO
        PersonProfileDTO dto = new PersonProfileDTO();
        dto.kind = kind;
        dto.email = u.getEmail();
        dto.sbuId = u.getStudentId();
        dto.firstName = u.getFirstName();
        dto.lastName = u.getLastName();
        dto.displayName = joinNames(u.getFirstName(), u.getLastName());

        // ******************** Map all fields from users table ********************
        dto.department = u.getDepartment();
        dto.officeHours = u.getOfficeHours();
        dto.officeLocation = u.getOfficeLocation();
        dto.academicUnit = u.getAcademicUnit(); 
        dto.unitLevel = u.getLevel(); 
        
        // Student/General fields
        dto.major = u.getMajor();           
        dto.gpa = u.getGpa();               
        dto.graduationYear = u.getGraduationYear(); 
        
        // Class Standing: Get from GPA summary endpoint
        if ("student".equals(kind)) {
             // For now, set to null - will be properly calculated from GPA endpoint which includes standing
             dto.classStanding = null;
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

        // Need to parse JSON String fields from database (temporarily null/empty list)
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

    // 🚨 Modified: Changed to public static
    public static TermRef parseTermRef(String jsonString) {
        if (jsonString == null || jsonString.isBlank() || jsonString.equals("null")) {
            return null;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(jsonString, TermRef.class);
        } catch (Exception e) {
            System.err.println("Error parsing TermRef JSON: " + jsonString + " - " + e.getMessage());
            return null;
        }
    }
    
    // 🚨 Modified: Changed to public static
    public static List<String> parseStrList(String jsonString) {
        if (jsonString == null || jsonString.isBlank() || jsonString.equals("[]") || jsonString.equals("null")) {
            return Collections.emptyList();
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            List<String> result = mapper.readValue(jsonString, List.class);
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Error parsing string list JSON: " + jsonString + " - " + e.getMessage());
            // Fallback: try simple comma-separated string
            return Arrays.asList(jsonString.replaceAll("[\\[\\]\"]", "").split(","));
        }
    }
    
    // 🚨 Modified: Changed to public static
    public static List<TermRef> parseTermList(String jsonString) {
        if (jsonString == null || jsonString.isBlank() || jsonString.equals("[]") || jsonString.equals("null")) {
            return Collections.emptyList();
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.core.type.TypeReference<List<TermRef>> typeRef = 
                new com.fasterxml.jackson.core.type.TypeReference<List<TermRef>>() {};
            List<TermRef> result = mapper.readValue(jsonString, typeRef);
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("Error parsing TermRef list JSON: " + jsonString + " - " + e.getMessage());
            return Collections.emptyList();
        }
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
        
        // ******************** Added DTO fields ********************
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