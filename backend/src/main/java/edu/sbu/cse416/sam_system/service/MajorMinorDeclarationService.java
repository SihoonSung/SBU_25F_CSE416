package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.AcademicEvent;
import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.repository.MajorMinorDeclarationRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.AcademicEventRepository;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * //tp : Used CURSOR to implement this :
 * Service for managing major/minor declaration requests.
 * Handles business logic for student declarations and advisor approvals.
 */
@Service
@Transactional
public class MajorMinorDeclarationService {
    
    @Autowired
    private MajorMinorDeclarationRepository declarationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AcademicEventRepository academicEventRepository;
    
    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;
    
    @Autowired
    private SystemDateService systemDateService;
    
    // Constants for limits
    private static final int MAX_MAJORS = 2;
    private static final int MAX_MINORS = 3;
    
    /**
     * Submit a new major/minor declaration request
     */
    // TP: Used cursor to submit a new declaration (simple overload)
    public MajorMinorDeclaration submitDeclaration(Long studentId, String subjectCode, 
                                                  DeclarationType declarationType,
                                                  String effectiveTerm) {
        return submitDeclaration(studentId, subjectCode, declarationType, effectiveTerm, null);
    }
    
    /**
     * Submit a new major/minor declaration request (with optional fromSubjectCode for change requests)
     */
    // TP: Used cursor to submit a new declaration (supports change requests)
    public MajorMinorDeclaration submitDeclaration(Long studentId, String subjectCode, 
                                                  DeclarationType declarationType,
                                                  String effectiveTerm, String fromSubjectCode) {
        
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        // Validate student is a student
        if (student.getRole() != edu.sbu.cse416.sam_system.entity.UserRole.STUDENT) {
            throw new IllegalArgumentException("Only students can declare majors/minors");
        }
        
        // Validate that the major/minor exists in degree requirements
        validateSubjectExists(subjectCode, declarationType);
        
        // Cross-type duplication rules:
        // - A minor cannot duplicate any existing major
        // - A major cannot duplicate any existing minor (defensive)
        if (declarationType == DeclarationType.MINOR) {
            if (subjectExistsInJsonArray(student.getMajors(), subjectCode)) {
                throw new IllegalArgumentException("You cannot declare a minor in '" + subjectCode +
                    "' because it matches one of your current majors.");
            }
        } else if (declarationType == DeclarationType.MAJOR) {
            if (subjectExistsInJsonArray(student.getMinors(), subjectCode)) {
                throw new IllegalArgumentException("You cannot declare a major in '" + subjectCode +
                    "' because it matches one of your current minors.");
            }
        }

        // Check for duplicate pending declarations
        if (declarationRepository.existsPendingDeclaration(student, subjectCode, declarationType)) {
            throw new IllegalArgumentException("A pending declaration for this subject already exists");
        }
        
        // Check if student already has this approved subject
        boolean hasApprovedSubject = declarationRepository.existsApprovedDeclaration(student, subjectCode, declarationType);
        
        if (hasApprovedSubject) {
            // Student already has this approved major/minor - reject duplicate
            throw new IllegalArgumentException("You already have an approved " + declarationType.toString().toLowerCase() + 
                " for " + subjectCode + ". Cannot declare the same " + declarationType.toString().toLowerCase() + " again.");
        }
        
        // If this is a change request (fromSubjectCode provided), don't check limits
        // Otherwise validate limits (2 majors, 3 minors)
        if (fromSubjectCode == null || fromSubjectCode.trim().isEmpty()) {
            validateDeclarationLimits(student, declarationType);
        }
        
        // Validate academic calendar dates (implement based on your calendar system)
        validateAcademicCalendarDates(effectiveTerm);
        
        // Create new declaration
        MajorMinorDeclaration declaration = new MajorMinorDeclaration();
        declaration.setStudent(student);
        declaration.setStudentNetId(student.getSbuId());
        declaration.setSubjectCode(subjectCode);
        declaration.setDeclarationType(declarationType);
        declaration.setEffectiveTerm(effectiveTerm);
        declaration.setSubmittedAt(LocalDateTime.now());
        
        // Store fromSubjectCode in advisor notes (temporary storage for change requests)
        if (fromSubjectCode != null && !fromSubjectCode.trim().isEmpty()) {
            declaration.setAdvisorNotes("CHANGE_FROM:" + fromSubjectCode);
        }
        
        // Determine if CEAS advisor approval is needed
        if (isCeasTransfer(student, subjectCode)) {
            // Set CEAS advisor (you'll need to implement logic to find CEAS advisor)
            User ceasAdvisor = findCeasAdvisor();
            declaration.setCeasAdvisor(ceasAdvisor);
        } else {
            // Set department advisor (you'll need to implement logic to find department advisor)
            User departmentAdvisor = findDepartmentAdvisor(subjectCode);
            declaration.setAdvisor(departmentAdvisor);
        }
        
        return declarationRepository.save(declaration);
    }

    private boolean subjectExistsInJsonArray(String jsonArray, String subjectCode) {
        if (jsonArray == null || jsonArray.trim().isEmpty()) return false;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<?> list = mapper.readValue(jsonArray, java.util.List.class);
            if (list == null) return false;
            for (Object o : list) {
                if (subjectCode.equals(String.valueOf(o))) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
    
    /**
     * Approve a declaration request
     */
    // TP: Used cursor to approve a declaration and update student records
    public MajorMinorDeclaration approveDeclaration(Long declarationId, Long advisorId, String notes) {
        MajorMinorDeclaration declaration = declarationRepository.findById(declarationId)
                .orElseThrow(() -> new IllegalArgumentException("Declaration not found"));
        
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        // Validate advisor has permission to approve
        if (!canAdvisorApprove(advisor, declaration)) {
            throw new IllegalArgumentException("Advisor does not have permission to approve this declaration");
        }
        
        // Preserve existing advisor notes if they contain CHANGE_FROM
        String existingNotes = declaration.getAdvisorNotes();
        String finalNotes = notes;
        if (existingNotes != null && existingNotes.startsWith("CHANGE_FROM:")) {
            // If notes are provided, append them after the CHANGE_FROM info
            if (notes != null && !notes.trim().isEmpty()) {
                finalNotes = existingNotes + " | Advisor: " + notes;
            } else {
                finalNotes = existingNotes;
            }
        }
        
        declaration.approve(advisor, finalNotes);
        declarationRepository.save(declaration);
        
        // Update student's majors/minors in User table
        updateStudentMajorsMinors(declaration);
        
        return declaration;
    }
    
    /**
     * Reject a declaration request
     */
    // TP: Used cursor to reject a declaration with advisor notes
    public MajorMinorDeclaration rejectDeclaration(Long declarationId, Long advisorId, String notes) {
        MajorMinorDeclaration declaration = declarationRepository.findById(declarationId)
                .orElseThrow(() -> new IllegalArgumentException("Declaration not found"));
        
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        // Validate advisor has permission to reject
        if (!canAdvisorApprove(advisor, declaration)) {
            throw new IllegalArgumentException("Advisor does not have permission to reject this declaration");
        }
        
        declaration.reject(advisor, notes);
        return declarationRepository.save(declaration);
    }
    
    /**
     * Get all declarations for a student
     */
    // TP: Used cursor to list all declarations for a student
    public List<MajorMinorDeclaration> getStudentDeclarations(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        return declarationRepository.findByStudentOrderByCreatedAtDesc(student);
    }
    
    /**
     * Get pending declarations for an advisor
     */
    // TP: Used cursor to list advisor's pending declarations
    public List<MajorMinorDeclaration> getPendingDeclarationsForAdvisor(Long advisorId) {
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        return declarationRepository.findByAdvisorOrCeasAdvisorOrderByCreatedAtDesc(advisor)
                .stream()
                .filter(MajorMinorDeclaration::isPending)
                .toList();
    }
    
    /**
     * Validate that the subject exists in degree requirements
     */
    // TP: Used cursor to validate subject availability from degree requirements
    private void validateSubjectExists(String subjectCode, DeclarationType declarationType) {
        // Check if subject exists in active degree requirements
        List<DegreeRequirement> requirements = degreeRequirementRepository
            .findBySubjectCodeAndActive(subjectCode);
        
        if (requirements.isEmpty()) {
            throw new IllegalArgumentException("The " + declarationType.toString().toLowerCase() + 
                " '" + subjectCode + "' does not exist or is not available for declaration");
        }
        
        // Validate that the specific declaration type exists
        boolean hasMatchingType = false;
        if (declarationType == DeclarationType.MINOR) {
            // Check for MINOR degree type
            hasMatchingType = requirements.stream()
                .anyMatch(req -> req.getDegreeType() == edu.sbu.cse416.sam_system.entity.DegreeType.MINOR);
        } else if (declarationType == DeclarationType.MAJOR) {
            // Check for BA or BS degree types
            hasMatchingType = requirements.stream()
                .anyMatch(req -> req.getDegreeType() == edu.sbu.cse416.sam_system.entity.DegreeType.BA ||
                                req.getDegreeType() == edu.sbu.cse416.sam_system.entity.DegreeType.BS);
        }
        
        if (!hasMatchingType) {
            throw new IllegalArgumentException("The " + declarationType.toString().toLowerCase() + 
                " '" + subjectCode + "' is not available for declaration. Please check degree requirements.");
        }
    }
    
    /**
     * Validate declaration limits (2 majors, 3 minors)
     */
    // TP: Used cursor to enforce major/minor count limits
    private void validateDeclarationLimits(User student, DeclarationType declarationType) {
        if (declarationType == DeclarationType.MAJOR) {
            // Just count current majors from User table (already approved declarations)
            // Don't double count by adding declaredMajors
            long currentMajors = countCurrentMajorsFromUser(student);
            
            System.out.println("DEBUG: Student " + student.getFirstName() + " " + student.getLastName() + 
                             " - Current majors: " + currentMajors);
            
            if (currentMajors >= MAX_MAJORS) {
                throw new IllegalArgumentException("Students can declare at most " + MAX_MAJORS + " majors");
            }
        } else if (declarationType == DeclarationType.MINOR) {
            // Just count current minors from User table (already approved declarations)
            long currentMinors = countCurrentMinorsFromUser(student);
            
            if (currentMinors >= MAX_MINORS) {
                throw new IllegalArgumentException("Students can declare at most " + MAX_MINORS + " minors");
            }
        }
    }
    
    /**
     * Count current majors from User table (JSON field)
     */
    // TP: Used cursor to count student's current majors from JSON
    private long countCurrentMajorsFromUser(User student) {
        if (student.getMajors() == null || student.getMajors().trim().isEmpty()) {
            return 0;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> majors = mapper.readValue(student.getMajors(), List.class);
            return majors != null ? majors.size() : 0;
        } catch (Exception e) {
            System.err.println("Error parsing majors JSON: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Count current minors from User table (JSON field)
     */
    // TP: Used cursor to count student's current minors from JSON
    private long countCurrentMinorsFromUser(User student) {
        if (student.getMinors() == null || student.getMinors().trim().isEmpty()) {
            return 0;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> minors = mapper.readValue(student.getMinors(), List.class);
            return minors != null ? minors.size() : 0;
        } catch (Exception e) {
            System.err.println("Error parsing minors JSON: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Registrar-style interpretation:
     * The declaration BEGIN date for a given term is recorded in the PREVIOUS term's section.
     * Example: Spring 2025 begin is stored under Fall 2024.
     */
    public Optional<LocalDate> getMajorMinorChangesBegin(String semester, Integer year) {
        String[] possibleTitles = {
            "Major And Minor Changes Begin",
            "major_and_minor_changes_begin",
            "Major Minor Changes Begin",
            "Declaration Period Begin"
        };

        // Look ONLY in the PREVIOUS term's section
        java.util.Map<String, Object> prevTerm = getPreviousTerm(semester, year);
        String prevSemester = (String) prevTerm.get("semester");
        Integer prevYear = (Integer) prevTerm.get("year");

        if (prevSemester == null || prevYear == null) {
            return Optional.empty();
        }

        for (String title : possibleTitles) {
            Optional<AcademicEvent> event =
                academicEventRepository.findByTitleAndSemesterAndYear(title, prevSemester, prevYear);
            if (event.isPresent()) {
                return Optional.of(event.get().getDate());
            }
        }

        // Case-insensitive keyword search limited to previous term
        List<AcademicEvent> prevEvents =
            academicEventRepository.findByTitleKeyword("major and minor changes begin");
        for (AcademicEvent event : prevEvents) {
            if (event.getSemester() != null && event.getSemester().equalsIgnoreCase(prevSemester) &&
                event.getYear() != null && event.getYear().equals(prevYear)) {
                return Optional.of(event.getDate());
            }
        }

        return Optional.empty();
    }
    
    /**
     * Registrar-style interpretation:
     * The declaration END date for a given term is recorded in the CURRENT term's section.
     * Example: Spring 2025 end is stored in Spring 2025 section.
     */
    public Optional<LocalDate> getMajorMinorChangesEnd(String semester, Integer year) {
        String[] possibleTitles = {
            "Major And Minor Changes End",
            "major_and_minor_changes_end",
            "Major Minor Changes End",
            "Declaration Period End"
        };

        // Look ONLY in the CURRENT term's section
        for (String title : possibleTitles) {
            Optional<AcademicEvent> event =
                academicEventRepository.findByTitleAndSemesterAndYear(title, semester, year);
            if (event.isPresent()) {
                return Optional.of(event.get().getDate());
            }
        }

        // Case-insensitive keyword search limited to current term
        List<AcademicEvent> events =
            academicEventRepository.findByTitleKeyword("major and minor changes end");
        for (AcademicEvent event : events) {
            if (event.getSemester() != null && event.getSemester().equalsIgnoreCase(semester) &&
                event.getYear() != null && event.getYear().equals(year)) {
                return Optional.of(event.getDate());
            }
        }

        return Optional.empty();
    }
    
    /**
     * Get the previous term (semester, year) before the given term
     * Spring -> Fall (previous year)
     * Fall -> Spring (same year)
     */
    // TP: Used cursor to calculate previous term for declaration begin date lookup
    private java.util.Map<String, Object> getPreviousTerm(String semester, Integer year) {
        java.util.Map<String, Object> prevTerm = new java.util.HashMap<>();
        if (semester == null || year == null) {
            return prevTerm;
        }
        
        if (semester.equalsIgnoreCase("Spring")) {
            // Spring -> Fall (previous year)
            prevTerm.put("semester", "Fall");
            prevTerm.put("year", year - 1);
        } else if (semester.equalsIgnoreCase("Fall")) {
            // Fall -> Spring (same year)
            prevTerm.put("semester", "Spring");
            prevTerm.put("year", year);
        } else {
            // Default to Fall previous year if unknown
            prevTerm.put("semester", "Fall");
            prevTerm.put("year", year - 1);
        }
        return prevTerm;
    }
    
    /**
     * Get the next term (semester, year) after the given term
     * Fall -> Spring (same year if Fall, next year if Spring)
     * Spring -> Fall (next year)
     */
    // TP: Used cursor to calculate next term for declaration end date lookup
    private java.util.Map<String, Object> getNextTerm(String semester, Integer year) {
        java.util.Map<String, Object> nextTerm = new java.util.HashMap<>();
        if (semester == null || year == null) {
            return nextTerm;
        }
        
        if (semester.equalsIgnoreCase("Fall")) {
            // Fall -> Spring (next year)
            nextTerm.put("semester", "Spring");
            nextTerm.put("year", year + 1);
        } else if (semester.equalsIgnoreCase("Spring")) {
            // Spring -> Fall (same year)
            nextTerm.put("semester", "Fall");
            nextTerm.put("year", year);
        } else {
            // Default to Spring next year if unknown
            nextTerm.put("semester", "Spring");
            nextTerm.put("year", year + 1);
        }
        return nextTerm;
    }
    
    /**
     * Get all available terms from academic calendar
     */
    // TP: Used cursor to get all available terms from academic calendar
    public List<java.util.Map<String, Object>> getAvailableTermsFromCalendar() {
        List<Object[]> distinctTerms = academicEventRepository.findDistinctSemesterAndYear();
        List<java.util.Map<String, Object>> terms = new java.util.ArrayList<>();
        
        for (Object[] term : distinctTerms) {
            String semester = (String) term[0];
            Integer year = (Integer) term[1];
            
            if (semester != null && year != null) {
                java.util.Map<String, Object> termMap = new java.util.HashMap<>();
                termMap.put("semester", semester);
                termMap.put("year", year);
                termMap.put("displayName", semester + " " + year);
                termMap.put("value", semester.toUpperCase() + " " + year);
                
                // Get declaration dates for this term using registrar-style rules:
                // begin: from previous term section, end: from current term section
                Optional<LocalDate> beginDate = getMajorMinorChangesBegin(semester, year);
                Optional<LocalDate> endDate = getMajorMinorChangesEnd(semester, year);
                
                if (beginDate.isPresent()) {
                    termMap.put("declarationBeginDate", beginDate.get().toString());
                }
                if (endDate.isPresent()) {
                    termMap.put("declarationEndDate", endDate.get().toString());
                }
                
                terms.add(termMap);
            }
        }
        
        return terms;
    }
    
    /**
     * Parse effectiveTerm to extract semester and year
     * Supports formats: "Fall 2025", "SPRING 2026", "Fall2025", etc.
     */
    // TP: Used cursor to parse effective term into semester and year
    private java.util.Map<String, Object> parseEffectiveTerm(String effectiveTerm) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        if (effectiveTerm == null || effectiveTerm.trim().isEmpty()) {
            return result;
        }
        
        String normalized = effectiveTerm.trim();
        // Handle formats like "Fall 2025", "SPRING 2026"
        String[] parts = normalized.split("\\s+");
        
        if (parts.length >= 2) {
            String semester = parts[0].trim();
            String yearStr = parts[parts.length - 1].trim();
            
            // Normalize semester
            if (semester.equalsIgnoreCase("FALL")) {
                semester = "Fall";
            } else if (semester.equalsIgnoreCase("SPRING")) {
                semester = "Spring";
            } else if (semester.equalsIgnoreCase("SUMMER")) {
                semester = "Summer";
            }
            
            try {
                Integer year = Integer.parseInt(yearStr);
                result.put("semester", semester);
                result.put("year", year);
            } catch (NumberFormatException e) {
                // Invalid year format
            }
        }
        
        return result;
    }
    
    /**
     * Validate academic calendar dates dynamically from AcademicEvents (registrar-style)
     */
    // TP: Used cursor to validate declaration period using academic events
    private void validateAcademicCalendarDates(String effectiveTerm) {
        if (effectiveTerm == null || effectiveTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("Effective term is required");
        }
        
        // Parse effectiveTerm to get semester and year
        java.util.Map<String, Object> termInfo = parseEffectiveTerm(effectiveTerm);
        String semester = (String) termInfo.get("semester");
        Integer year = (Integer) termInfo.get("year");
        
        if (semester == null || year == null) {
            // If parsing fails, fall back to old logic
            System.out.println("WARNING: Could not parse effectiveTerm: " + effectiveTerm + ", using fallback");
            validateAcademicCalendarDatesFallback(effectiveTerm);
            return;
        }
        
        // Get current date from system date service (respects registrar's override)
        LocalDate currentDate = systemDateService.today();
        
        // Get dates from academic calendar following strict registrar-style mapping:
        // begin is stored in previous term; end is stored in next term
        Optional<LocalDate> beginDateOpt = getMajorMinorChangesBegin(semester, year);
        Optional<LocalDate> endDateOpt = getMajorMinorChangesEnd(semester, year);

        // Edge cases per registrar-style rules:
        // - If only begin exists (no next term yet): allow from begin onward (no upper bound)
        // - If only end exists (no previous term in calendar): allow until end (no lower bound)
        LocalDate startDate = beginDateOpt.orElse(null);
        LocalDate endDate = endDateOpt.orElse(null);

        if (startDate == null && endDate == null) {
            throw new IllegalArgumentException(
                "Declaration period dates are not available in academic calendar for " + semester + " " + year + "."
            );
        }
        
        // Validate if current date is within the declaration period (inclusive bounds where present)
        if (startDate != null && currentDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                "Declaration period for " + effectiveTerm + " has not started yet. It begins on " + startDate + "."
            );
        }
        if (endDate != null && currentDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                "Declaration period for " + effectiveTerm + " has ended on " + endDate + "."
            );
        }
        // Valid when currentDate >= startDate (if present) and currentDate <= endDate (if present)
    }
    
    /**
     * Fallback method with hardcoded dates for backward compatibility
     */
    // TP: Used cursor to validate dates using fallback hardcoded ranges
    private void validateAcademicCalendarDatesFallback(String effectiveTerm) {
        LocalDate currentDate = systemDateService.today();
        
        if ("FALL 2025".equals(effectiveTerm)) {
            LocalDate startDate = LocalDate.of(2025, 3, 31);
            LocalDate endDate = LocalDate.of(2025, 8, 29);
            
            // Inclusive of both start and end dates: currentDate >= startDate && currentDate <= endDate
            if (currentDate.isBefore(startDate) || currentDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Declaration period for Fall 2025 has ended. Please check the academic calendar for available terms.");
            }
        } else if ("SPRING 2026".equals(effectiveTerm)) {
            LocalDate startDate = LocalDate.of(2025, 10, 27);
            LocalDate endDate = LocalDate.of(2026, 1, 30);
            
            // Inclusive of both start and end dates: currentDate >= startDate && currentDate <= endDate
            if (currentDate.isBefore(startDate) || currentDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Declaration period for Spring 2026 has ended. Please check the academic calendar for available terms.");
            }
        } else {
            throw new IllegalArgumentException("Invalid effective term. Please select a valid term.");
        }
    }
    
    /**
     * Check if this is a CEAS transfer
     */
    // TP Used cursor to check if declaration requires CEAS transfer
    private boolean isCeasTransfer(User student, String subjectCode) {
        // CEAS subjects: AMS, BME, CME, CIV, ECE, CSE, ESE, ESG, ISE, MEC, TSM
        if (!isCeasMajor(subjectCode)) {
            return false;
        }
        
        // Check if student is already in CEAS (has AOI or declared major in CEAS)
        return !isStudentInCeas(student);
    }
    
    /**
     * Check if a subject is a CEAS major
     */
    // TP Used cursor to check if subject is a CEAS major
    private boolean isCeasMajor(String subjectCode) {
        String[] ceasSubjects = {"AMS", "BME", "CME", "CIV", "ECE", "CSE", "ESE", "ESG", "ISE", "MEC", "TSM"};
        for (String ceasSubject : ceasSubjects) {
            if (ceasSubject.equals(subjectCode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if student is already in CEAS
     */
    // TP Used cursor to check if student is already in CEAS
    private boolean isStudentInCeas(User student) {
        // Check if student has AOI (Area of Interest) in CEAS
        if (student.getAoi() != null && !student.getAoi().trim().isEmpty()) {
            if (isCeasMajor(student.getAoi())) {
                return true;
            }
        }
        
        // Check if student has any approved CEAS majors
        if (student.getMajors() == null || student.getMajors().trim().isEmpty()) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> majors = mapper.readValue(student.getMajors(), List.class);
            if (majors != null) {
                for (Object major : majors) {
                    String majorStr = major.toString();
                    if (isCeasMajor(majorStr)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing majors JSON: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Find CEAS advisor
     */
    // TP Used cursor to find CEAS advisor for CEAS transfers
    private User findCeasAdvisor() {
        List<User> allAdvisors = userRepository.findByRole(edu.sbu.cse416.sam_system.entity.UserRole.ADVISOR);
        
        // Priority 1: Find advisor with level "CEAS" or "ceas" (case insensitive)
        for (User advisor : allAdvisors) {
            if (advisor.getLevel() != null && "CEAS".equalsIgnoreCase(advisor.getLevel())) {
                return advisor;
            }
        }
        
        // Priority 2: Find advisor with academic_unit containing any CEAS subject
        for (User advisor : allAdvisors) {
            if (advisor.getAcademicUnit() != null) {
                String academicUnit = advisor.getAcademicUnit();
                // Check if advisor's academic unit contains any CEAS subject
                String[] ceasSubjects = {"AMS", "BME", "CME", "CIV", "ECE", "CSE", "ESE", "ESG", "ISE", "MEC", "TSM"};
                for (String ceasSubject : ceasSubjects) {
                    if (academicUnit.contains(ceasSubject)) {
                        return advisor;
                    }
                }
            }
        }
        
        // Priority 3: Fallback to university level advisor
        for (User advisor : allAdvisors) {
            if ("university".equalsIgnoreCase(advisor.getLevel())) {
                return advisor;
            }
        }
        
        throw new IllegalStateException("No CEAS advisor found");
    }
    
    /**
     * Find department advisor for subject
     */
    // TP: Used cursor to resolve department advisor for subject
    private User findDepartmentAdvisor(String subjectCode) {
        // Try to find advisor with exact academic_unit match
        List<User> advisors = userRepository.findByRoleAndAcademicUnit(
                edu.sbu.cse416.sam_system.entity.UserRole.ADVISOR, subjectCode);
        
        if (!advisors.isEmpty()) {
            return advisors.get(0);
        }
        
        // Fallback: find any advisor with subject in academic_unit (but prefer department-level with fewer subjects)
        List<User> allAdvisors = userRepository.findByRole(edu.sbu.cse416.sam_system.entity.UserRole.ADVISOR);
        User bestMatch = null;
        int minSubjects = Integer.MAX_VALUE;
        
        for (User advisor : allAdvisors) {
            if (advisor.getAcademicUnit() != null && advisor.getAcademicUnit().contains(subjectCode)) {
                // Count subjects in academic_unit (by counting commas)
                int subjectCount = advisor.getAcademicUnit().split(",").length;
                // Prefer advisors with fewer subjects (more specific)
                if (bestMatch == null || subjectCount < minSubjects) {
                    bestMatch = advisor;
                    minSubjects = subjectCount;
                }
            }
        }
        
        if (bestMatch != null) {
            return bestMatch;
        }
        
        // Final fallback: find any department-level advisor
        List<User> departmentAdvisors = userRepository.findByAdvisorLevel("department");
        if (!departmentAdvisors.isEmpty()) {
            return departmentAdvisors.get(0);
        }
        
        // Ultimate fallback: find university-level advisor
        List<User> universityAdvisors = userRepository.findByAdvisorLevel("university");
        if (!universityAdvisors.isEmpty()) {
            return universityAdvisors.get(0);
        }
        
        throw new IllegalStateException("No advisor found for " + subjectCode);
    }
    
    /**
     * Check if advisor can approve this declaration
     */
    // TP Used cursor to check if advisor can approve declaration
    private boolean canAdvisorApprove(User advisor, MajorMinorDeclaration declaration) {
        // Check if advisor is the assigned advisor or CEAS advisor
        if ((declaration.getAdvisor() != null && declaration.getAdvisor().equals(advisor)) ||
            (declaration.getCeasAdvisor() != null && declaration.getCeasAdvisor().equals(advisor))) {
            return true;
        }
        
        // Additional permission checks based on advisor type and subject
        String subjectCode = declaration.getSubjectCode();
        
        // University-level advisors can approve anything
        if ("university".equalsIgnoreCase(advisor.getLevel())) {
            return true;
        }
        
        // CEAS level advisors can approve all CEAS subjects
        if (advisor.getLevel() != null && "CEAS".equalsIgnoreCase(advisor.getLevel())) {
            return isCeasMajor(subjectCode);
        }
        
        // CEAS advisors (by academic_unit) can approve all CEAS subjects
        if (advisor.getAcademicUnit() != null) {
            String academicUnit = advisor.getAcademicUnit();
            String[] ceasSubjects = {"AMS", "BME", "CME", "CIV", "ECE", "CSE", "ESE", "ESG", "ISE", "MEC", "TSM"};
            for (String ceasSubject : ceasSubjects) {
                if (academicUnit.contains(ceasSubject)) {
                    // This is a CEAS advisor, can approve any CEAS subject
                    return isCeasMajor(subjectCode);
                }
            }
        }
        
        // Department advisors can approve their subjects
        if (advisor.getAcademicUnit() != null && advisor.getAcademicUnit().contains(subjectCode)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get all declarations for advisor
     */
    // TP: Used cursor to list all declarations assigned to an advisor
    public List<MajorMinorDeclaration> getAllDeclarationsForAdvisor(Long advisorId) {
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        if (!advisor.isAdvisor()) {
            throw new IllegalArgumentException("User is not an advisor");
        }
        
        return declarationRepository.findByAdvisorOrCeasAdvisorOrderByCreatedAtDesc(advisor);
    }
    
    /**
     * Withdraw a declaration (PENDING only)
     * Once approved, declarations cannot be withdrawn
     */
    // TP: Used cursor to withdraw a pending declaration owned by the student
    public MajorMinorDeclaration withdrawDeclaration(Long declarationId, Long studentId) {
        MajorMinorDeclaration declaration = declarationRepository.findById(declarationId)
                .orElseThrow(() -> new IllegalArgumentException("Declaration not found"));
        
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        // Check if student owns this declaration
        if (!declaration.getStudent().equals(student)) {
            throw new IllegalArgumentException("You can only withdraw your own declarations");
        }
        
        // Only allow withdrawal of PENDING declarations
        if (!declaration.isPending()) {
            throw new IllegalArgumentException("Cannot withdraw an approved or rejected declaration. Once a major or minor is approved, it cannot be removed.");
        }
        
        // Delete PENDING declaration from database
        declarationRepository.delete(declaration);
        return null; // Deleted
    }
    
    /**
     * Get declaration by ID
     */
    // TP: Used cursor to fetch a declaration by its ID
    public Optional<MajorMinorDeclaration> getDeclarationById(Long declarationId) {
        return declarationRepository.findById(declarationId);
    }
    
    /**
     * Update student's majors/minors in User table after approval
     */
    // TP: Used cursor to sync student's majors/minors after approval
    private void updateStudentMajorsMinors(MajorMinorDeclaration declaration) {
        try {
            User student = declaration.getStudent();
            String subjectCode = declaration.getSubjectCode();
            
            // Check if this is a change request (from advisor notes)
            String advisorNotes = declaration.getAdvisorNotes();
            String fromSubjectCode = null;
            boolean isChangeRequest = false;
            if (advisorNotes != null && advisorNotes.startsWith("CHANGE_FROM:")) {
                fromSubjectCode = advisorNotes.substring("CHANGE_FROM:".length());
                isChangeRequest = true;
            }
            
            if (declaration.getDeclarationType() == DeclarationType.MAJOR) {
                // Add to majors JSON array
                String majorsJson = student.getMajors();
                List<String> majors = new java.util.ArrayList<>();
                
                if (majorsJson != null && !majorsJson.trim().isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        List<String> parsedMajors = mapper.readValue(majorsJson, List.class);
                        majors = parsedMajors;
                    } catch (Exception e) {
                        System.err.println("Error parsing majors JSON: " + e.getMessage());
                    }
                }
                
                // If this is a change request, replace at the same index to preserve ordering/primary major
                if (isChangeRequest && fromSubjectCode != null) {
                    int idx = majors.indexOf(fromSubjectCode);
                    if (idx >= 0) {
                        majors.set(idx, subjectCode);
                        System.out.println("Replaced major " + fromSubjectCode + " -> " + subjectCode + " at index " + idx);
                    } else {
                        // Fallback: remove if present then add
                        majors.remove(fromSubjectCode);
                        if (!majors.contains(subjectCode)) {
                            majors.add(subjectCode);
                        }
                        System.out.println("Replaced major (fallback) " + fromSubjectCode + " -> " + subjectCode);
                    }
                } else {
                    // Add new major (duplicate check already done in submitDeclaration)
                    if (!majors.contains(subjectCode)) {
                        majors.add(subjectCode);
                    }
                }
                
                // Update JSON and sync single major field (first major)
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String updatedMajorsJson = mapper.writeValueAsString(majors);
                student.setMajors(updatedMajorsJson);
                // Keep legacy single major column in sync with the first major in the list
                String primaryMajor = majors.isEmpty() ? null : majors.get(0);
                student.setMajor(primaryMajor);
                
                // TP used cursor to: Also update degrees array with "BA" or "BS" based on degree requirements
                
            } else if (declaration.getDeclarationType() == DeclarationType.MINOR) {
                // Add to minors JSON array
                String minorsJson = student.getMinors();
                List<String> minors = new java.util.ArrayList<>();
                
                if (minorsJson != null && !minorsJson.trim().isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        List<String> parsedMinors = mapper.readValue(minorsJson, List.class);
                        minors = parsedMinors;
                    } catch (Exception e) {
                        System.err.println("Error parsing minors JSON: " + e.getMessage());
                    }
                }
                
                // If this is a change request, remove the old subject first
                if (isChangeRequest && fromSubjectCode != null) {
                    minors.remove(fromSubjectCode);
                    System.out.println("Removed old minor " + fromSubjectCode + " due to change request");
                }
                
                // Add new minor (duplicate check already done in submitDeclaration)
                if (!minors.contains(subjectCode)) {
                    minors.add(subjectCode);
                }
                
                // Update JSON
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String updatedMinorsJson = mapper.writeValueAsString(minors);
                student.setMinors(updatedMinorsJson);
            }
            
            // Save updated student
            userRepository.save(student);
            
        } catch (Exception e) {
            System.err.println("Error updating student majors/minors: " + e.getMessage());
            e.printStackTrace();
        }
    }
}