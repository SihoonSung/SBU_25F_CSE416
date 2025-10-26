package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.*;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import edu.sbu.cse416.sam_system.repository.MajorMinorDeclarationRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(
    origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://localhost:5175"},
    allowCredentials = "true"
)
@RestController
@RequestMapping("/api/test-data")
public class TestDataController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;

    @Autowired
    private MajorMinorDeclarationRepository declarationRepository;

    /**
     * Create test data for major/minor declaration system
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createTestData() {
        try {
            // Create test users
            createTestUsers();
            
            // Create test degree requirements
            createTestDegreeRequirements();
            
            // Create test declarations
            createTestDeclarations();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test data created successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to create test data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private void createTestUsers() {
        // Create test student
        User student = new User();
        student.setSbuId("test-student-001");
        student.setEmail("student@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setRole(UserRole.STUDENT);
        student.setGoogleId("test-student-google-id");
        student.setIsActive(true);
        student.setStudentId("123456789");
        student.setGpa(3.5);
        userRepository.save(student);

        // Create test advisor
        User advisor = new User();
        advisor.setSbuId("test-advisor-001");
        advisor.setEmail("advisor@test.com");
        advisor.setFirstName("Jane");
        advisor.setLastName("Smith");
        advisor.setRole(UserRole.ADVISOR);
        advisor.setGoogleId("test-advisor-google-id");
        advisor.setIsActive(true);
        advisor.setLevel("department");
        advisor.setAcademicUnit("CSE");
        userRepository.save(advisor);

        // Create test registrar
        User registrar = new User();
        registrar.setSbuId("test-registrar-001");
        registrar.setEmail("registrar@test.com");
        registrar.setFirstName("Admin");
        registrar.setLastName("User");
        registrar.setRole(UserRole.REGISTRAR);
        registrar.setGoogleId("test-registrar-google-id");
        registrar.setIsActive(true);
        userRepository.save(registrar);
    }

    private void createTestDegreeRequirements() {
        // CSE Major (Restricted)
        DegreeRequirement cseMajor = new DegreeRequirement();
        cseMajor.setSubjectCode("CSE");
        cseMajor.setDegreeType(DegreeType.BS);
        cseMajor.setRequirementsVersion("2024-2025");
        cseMajor.setEffectiveTerm("Fall 2024");
        cseMajor.setTotalCreditsRequired(120);
        cseMajor.setIsRestricted(true);
        cseMajor.setGpaRequirement(3.0);
        cseMajor.setAdmissionRequirements("Completion of CSE 114, 214, 215 with grade of C or better");
        cseMajor.setCourseRequirements("CSE 114, 214, 215, 219, 220, 300, 373, 416");
        cseMajor.setPrerequisiteCourses("MATH 131, 132, AMS 151, 161");
        cseMajor.setCreatedBy("system");
        cseMajor.setIsActive(true);
        degreeRequirementRepository.save(cseMajor);

        // CSE Minor
        DegreeRequirement cseMinor = new DegreeRequirement();
        cseMinor.setSubjectCode("CSE");
        cseMinor.setDegreeType(DegreeType.MINOR);
        cseMinor.setRequirementsVersion("2024-2025");
        cseMinor.setEffectiveTerm("Fall 2024");
        cseMinor.setTotalCreditsRequired(18);
        cseMinor.setIsRestricted(false);
        cseMinor.setCreatedBy("system");
        cseMinor.setIsActive(true);
        degreeRequirementRepository.save(cseMinor);

        // AMS Major (Non-restricted)
        DegreeRequirement amsMajor = new DegreeRequirement();
        amsMajor.setSubjectCode("AMS");
        amsMajor.setDegreeType(DegreeType.BS);
        amsMajor.setRequirementsVersion("2024-2025");
        amsMajor.setEffectiveTerm("Fall 2024");
        amsMajor.setTotalCreditsRequired(120);
        amsMajor.setIsRestricted(false);
        amsMajor.setCreatedBy("system");
        amsMajor.setIsActive(true);
        degreeRequirementRepository.save(amsMajor);

        // BIO Major (Restricted)
        DegreeRequirement bioMajor = new DegreeRequirement();
        bioMajor.setSubjectCode("BIO");
        bioMajor.setDegreeType(DegreeType.BS);
        bioMajor.setRequirementsVersion("2024-2025");
        bioMajor.setEffectiveTerm("Fall 2024");
        bioMajor.setTotalCreditsRequired(120);
        bioMajor.setIsRestricted(true);
        bioMajor.setGpaRequirement(2.8);
        bioMajor.setAdmissionRequirements("Completion of BIO 201, 202 with grade of C or better");
        bioMajor.setCourseRequirements("BIO 201, 202, 203, 204, 205, 206");
        bioMajor.setCreatedBy("system");
        bioMajor.setIsActive(true);
        degreeRequirementRepository.save(bioMajor);
    }

    private void createTestDeclarations() {
        // Get test student and advisor
        User student = userRepository.findByEmail("student@test.com").orElse(null);
        User advisor = userRepository.findByEmail("advisor@test.com").orElse(null);

        if (student != null && advisor != null) {
            // Create pending CSE major declaration
            MajorMinorDeclaration cseDeclaration = new MajorMinorDeclaration();
            cseDeclaration.setStudent(student);
            cseDeclaration.setSubjectCode("CSE");
            cseDeclaration.setDeclarationType(DeclarationType.MAJOR);
            cseDeclaration.setEffectiveTerm("Fall 2025");
            cseDeclaration.setStatus(DeclarationStatus.PENDING);
            cseDeclaration.setSubmittedAt(LocalDateTime.now());
            declarationRepository.save(cseDeclaration);

            // Create approved AMS minor declaration
            MajorMinorDeclaration amsDeclaration = new MajorMinorDeclaration();
            amsDeclaration.setStudent(student);
            amsDeclaration.setSubjectCode("AMS");
            amsDeclaration.setDeclarationType(DeclarationType.MINOR);
            amsDeclaration.setEffectiveTerm("Fall 2024");
            amsDeclaration.setStatus(DeclarationStatus.APPROVED);
            amsDeclaration.setAdvisor(advisor);
            amsDeclaration.setAdvisorNotes("Approved - student meets all requirements");
            amsDeclaration.setSubmittedAt(LocalDateTime.now().minusDays(30));
            amsDeclaration.setReviewedAt(LocalDateTime.now().minusDays(25));
            declarationRepository.save(amsDeclaration);
        }
    }
}
