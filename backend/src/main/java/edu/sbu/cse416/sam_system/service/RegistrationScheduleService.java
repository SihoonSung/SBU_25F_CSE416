package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.RegistrationSchedule;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.repository.RegistrationScheduleRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.AcademicEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing registration schedules.
 * 
 * Features:
 * - Create and manage registration schedules for student categories
 * - Check if a student can register based on schedule and credits
 * - Calculate cumulative credits for students
 * - Calculate class standing based on credits
 * 
 * TP: Used Cursor to implement registration schedule service
 */
@Service
@Transactional
public class RegistrationScheduleService {
    
    @Autowired
    private RegistrationScheduleRepository scheduleRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserClassesRepository userClassesRepository;
    
    @Autowired
    private SystemDateService systemDateService;
    
    @Autowired
    private AcademicEventRepository academicEventRepository;
    
    /**
     * Normalize class standing to standard format
     * U1, FRESHMEN, FRESHMAN -> FRESHMAN
     * U2, SOPHOMORE -> SOPHOMORE
     * U3, JUNIOR -> JUNIOR
     * U4, SENIOR -> SENIOR
     */
    private String normalizeClassStanding(String classStanding) {
        if (classStanding == null || classStanding.trim().isEmpty()) {
            return null;
        }
        String normalized = classStanding.trim().toUpperCase();
        
        // Handle U1-U4 format
        if ("U1".equals(normalized) || "FRESHMEN".equals(normalized) || "FRESHMAN".equals(normalized)) {
            return "FRESHMAN";
        } else if ("U2".equals(normalized) || "SOPHOMORE".equals(normalized)) {
            return "SOPHOMORE";
        } else if ("U3".equals(normalized) || "JUNIOR".equals(normalized)) {
            return "JUNIOR";
        } else if ("U4".equals(normalized) || "SENIOR".equals(normalized)) {
            return "SENIOR";
        }
        return normalized; // Return as-is if not recognized
    }
    
    /**
     * Get credits range for a class standing
     * Returns [minCredits, maxCredits] based on standard ranges
     */
    private java.util.Map<String, Integer> getCreditsRangeForClassStanding(String classStanding) {
        java.util.Map<String, Integer> range = new java.util.HashMap<>();
        String normalized = normalizeClassStanding(classStanding);
        
        if ("FRESHMAN".equals(normalized)) {
            range.put("minCredits", 0);
            range.put("maxCredits", 24);
        } else if ("SOPHOMORE".equals(normalized)) {
            range.put("minCredits", 24);
            range.put("maxCredits", 57);
        } else if ("JUNIOR".equals(normalized)) {
            range.put("minCredits", 57);
            range.put("maxCredits", 85);
        } else if ("SENIOR".equals(normalized)) {
            range.put("minCredits", 85);
            range.put("maxCredits", null); // No upper limit
        }
        
        return range;
    }
    
    /**
     * Get the next term after the given term
     * Fall -> Spring (next year)
     * Spring -> Fall (same year)
     * Summer -> Fall (same year)
     */
    private java.util.Map<String, Object> getNextTerm(String semester, Integer year) {
        java.util.Map<String, Object> nextTerm = new java.util.HashMap<>();
        if ("Fall".equalsIgnoreCase(semester)) {
            nextTerm.put("semester", "Spring");
            nextTerm.put("year", year + 1);
        } else if ("Spring".equalsIgnoreCase(semester)) {
            nextTerm.put("semester", "Fall");
            nextTerm.put("year", year);
        } else if ("Summer".equalsIgnoreCase(semester)) {
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
     * Get advanced_registration_begins date from academic calendar for a term
     */
    public Optional<LocalDate> getAdvancedRegistrationBegins(String semester, Integer year) {
        // Try different title formats
        String[] possibleTitles = {
            "Advanced Registration Begins",
            "advanced_registration_begins",
            "Advanced Registration",
            "Registration Begins"
        };
        
        for (String title : possibleTitles) {
            Optional<edu.sbu.cse416.sam_system.entity.AcademicEvent> event = 
                academicEventRepository.findByTitleAndSemesterAndYear(title, semester, year);
            if (event.isPresent()) {
                return Optional.of(event.get().getDate());
            }
        }
        
        // Try case-insensitive search by title keyword
        List<edu.sbu.cse416.sam_system.entity.AcademicEvent> events = 
            academicEventRepository.findByTitleKeyword("advanced registration");
        for (edu.sbu.cse416.sam_system.entity.AcademicEvent event : events) {
            if (event.getSemester() != null && event.getSemester().equalsIgnoreCase(semester) &&
                event.getYear() != null && event.getYear().equals(year)) {
                return Optional.of(event.getDate());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Get late_registration_ends date from academic calendar for a term
     */
    public Optional<LocalDate> getLateRegistrationEnds(String semester, Integer year) {
        // Try different title formats
        String[] possibleTitles = {
            "Late Registration Ends",
            "late_registration_ends",
            "Late Registration",
            "Registration Ends"
        };
        
        for (String title : possibleTitles) {
            Optional<edu.sbu.cse416.sam_system.entity.AcademicEvent> event = 
                academicEventRepository.findByTitleAndSemesterAndYear(title, semester, year);
            if (event.isPresent()) {
                return Optional.of(event.get().getDate());
            }
        }
        
        // Try case-insensitive search by title keyword
        List<edu.sbu.cse416.sam_system.entity.AcademicEvent> events = 
            academicEventRepository.findByTitleKeyword("late registration");
        for (edu.sbu.cse416.sam_system.entity.AcademicEvent event : events) {
            if (event.getSemester() != null && event.getSemester().equalsIgnoreCase(semester) &&
                event.getYear() != null && event.getYear().equals(year)) {
                return Optional.of(event.getDate());
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Calculate cumulative credits for a student
     * Only counts classes with grades (completed courses)
     */
    public Integer calculateCumulativeCredits(Long userId) {
        List<UserClasses> completedClasses = userClassesRepository.findCompletedClassesByUserId(userId);
        return completedClasses.stream()
            .mapToInt(uc -> uc.getCredits() != null ? uc.getCredits() : 0)
            .sum();
    }
    
    /**
     * Calculate class standing based on cumulative credits
     * FRESHMAN (U1): 0-23 credits
     * SOPHOMORE (U2): 24-56 credits
     * JUNIOR (U3): 57-84 credits
     * SENIOR (U4): 85+ credits
     */
    public String calculateClassStanding(Integer cumulativeCredits) {
        if (cumulativeCredits == null || cumulativeCredits < 0) {
            return "FRESHMAN";
        }
        if (cumulativeCredits < 24) {
            return "FRESHMAN";
        } else if (cumulativeCredits < 57) {
            return "SOPHOMORE";
        } else if (cumulativeCredits < 85) {
            return "JUNIOR";
        } else {
            return "SENIOR";
        }
    }
    
    /**
     * Check if a student can register for a specific term
     * @param userId Student ID
     * @param semester Semester (Fall, Spring, Summer)
     * @param year Year
     * @return true if student can register, false otherwise
     */
    public boolean canStudentRegister(Long userId, String semester, Integer year) {
        // Get current date from SystemDateService (may be overridden)
        LocalDate currentDate = systemDateService.today();
        
        // Calculate student's cumulative credits
        Integer cumulativeCredits = calculateCumulativeCredits(userId);
        String classStanding = calculateClassStanding(cumulativeCredits);
        
        // Find all active schedules for the term
        List<RegistrationSchedule> activeSchedules = scheduleRepository.findActiveSchedulesForTerm(
            semester, year, currentDate);
        
        // Check if any schedule matches the student's criteria
        for (RegistrationSchedule schedule : activeSchedules) {
            if (schedule.matchesStudent(cumulativeCredits, classStanding)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get registration eligibility details for a student
     * @param userId Student ID
     * @param semester Semester
     * @param year Year
     * @return Map with eligibility details
     */
    public java.util.Map<String, Object> getRegistrationEligibility(Long userId, String semester, Integer year) {
        LocalDate currentDate = systemDateService.today();
        Integer cumulativeCredits = calculateCumulativeCredits(userId);
        String classStanding = calculateClassStanding(cumulativeCredits);
        
        // Find all active schedules for the term
        List<RegistrationSchedule> activeSchedules = scheduleRepository.findActiveSchedulesForTerm(
            semester, year, currentDate);
        
        // Filter matching schedules
        List<RegistrationSchedule> matchingSchedules = activeSchedules.stream()
            .filter(schedule -> schedule.matchesStudent(cumulativeCredits, classStanding))
            .toList();
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("eligible", !matchingSchedules.isEmpty());
        result.put("cumulativeCredits", cumulativeCredits);
        result.put("classStanding", classStanding);
        result.put("currentDate", currentDate.toString());
        result.put("matchingSchedules", matchingSchedules.stream()
            .map(this::buildScheduleResponse)
            .toList());
        
        // If not eligible, find all schedules for the term to show what's available
        if (matchingSchedules.isEmpty()) {
            List<RegistrationSchedule> allSchedules = scheduleRepository.findBySemesterAndYear(semester, year);
            result.put("allSchedules", allSchedules.stream()
                .map(this::buildScheduleResponse)
                .toList());
        }
        
        return result;
    }
    
    /**
     * Create a new registration schedule
     * @param semester Semester
     * @param year Year
     * @param classStanding Optional class standing
     * @param minCredits Optional minimum credits threshold
     * @param maxCredits Optional maximum credits threshold
     * @param startDate Registration start date
     * @param endDate Registration end date
     * @param createdById Registrar ID creating this schedule
     * @param description Optional description
     * @return Created schedule
     */
    public RegistrationSchedule createSchedule(String semester, Integer year,
                                               String classStanding, Integer minCredits, Integer maxCredits,
                                               LocalDate startDate, LocalDate endDate,
                                               Long createdById, String description) {
        // Normalize class standing
        String normalizedClassStanding = normalizeClassStanding(classStanding);
        
        // If class standing is specified but credits are not, auto-fill credits range
        if (normalizedClassStanding != null && minCredits == null && maxCredits == null) {
            java.util.Map<String, Integer> creditsRange = getCreditsRangeForClassStanding(normalizedClassStanding);
            minCredits = creditsRange.get("minCredits");
            maxCredits = creditsRange.get("maxCredits");
        }
        
        // Validate: at least one threshold should be set
        if (normalizedClassStanding == null && minCredits == null && maxCredits == null) {
            throw new RuntimeException("At least one of classStanding, minCredits, or maxCredits must be specified");
        }
        
        // If startDate is null, get from academic calendar
        if (startDate == null) {
            Optional<LocalDate> advancedRegDate = getAdvancedRegistrationBegins(semester, year);
            if (advancedRegDate.isPresent()) {
                startDate = advancedRegDate.get();
            } else {
                throw new RuntimeException("startDate is required when 'advanced_registration_begins' is not found in academic calendar for " + semester + " " + year);
            }
        }
        
        // If endDate is null, try to get from next term's academic calendar
        // If next term doesn't exist or doesn't have the date, leave it as null (registration never ends)
        if (endDate == null) {
            java.util.Map<String, Object> nextTerm = getNextTerm(semester, year);
            String nextSemester = (String) nextTerm.get("semester");
            Integer nextYear = (Integer) nextTerm.get("year");
            
            Optional<LocalDate> lateRegEndDate = getLateRegistrationEnds(nextSemester, nextYear);
            if (lateRegEndDate.isPresent()) {
                endDate = lateRegEndDate.get();
            }
            // If next term's late_registration_ends is not found, endDate remains null
            // This means registration never ends for this schedule
        }
        
        // Only validate if both dates are set
        if (endDate != null && startDate.isAfter(endDate)) {
            throw new RuntimeException("Start date must be before or equal to end date");
        }
        
        User createdBy = userRepository.findById(createdById)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        RegistrationSchedule schedule = new RegistrationSchedule(semester, year, startDate, endDate);
        schedule.setClassStanding(normalizedClassStanding); // Use normalized version
        schedule.setMinCredits(minCredits);
        schedule.setMaxCredits(maxCredits);
        schedule.setCreatedBy(createdBy);
        schedule.setDescription(description);
        
        return scheduleRepository.save(schedule);
    }
    
    /**
     * Get all schedules for a term
     */
    public List<RegistrationSchedule> getSchedulesForTerm(String semester, Integer year) {
        return scheduleRepository.findBySemesterAndYear(semester, year);
    }
    
    /**
     * Get schedule by ID
     */
    public RegistrationSchedule getScheduleById(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new RuntimeException("Schedule not found"));
    }
    
    /**
     * Update a schedule
     */
    public RegistrationSchedule updateSchedule(Long scheduleId, String semester, Integer year,
                                               String classStanding, Integer minCredits, Integer maxCredits,
                                               LocalDate startDate, LocalDate endDate, String description) {
        RegistrationSchedule schedule = getScheduleById(scheduleId);
        
        // Normalize class standing
        String normalizedClassStanding = normalizeClassStanding(classStanding);
        
        // If class standing is specified but credits are not, auto-fill credits range
        if (normalizedClassStanding != null && minCredits == null && maxCredits == null) {
            java.util.Map<String, Integer> creditsRange = getCreditsRangeForClassStanding(normalizedClassStanding);
            minCredits = creditsRange.get("minCredits");
            maxCredits = creditsRange.get("maxCredits");
        }
        
        if (normalizedClassStanding == null && minCredits == null && maxCredits == null) {
            throw new RuntimeException("At least one of classStanding, minCredits, or maxCredits must be specified");
        }
        
        // Only validate if both dates are set
        if (endDate != null && startDate.isAfter(endDate)) {
            throw new RuntimeException("Start date must be before or equal to end date");
        }
        
        schedule.setSemester(semester);
        schedule.setYear(year);
        schedule.setClassStanding(normalizedClassStanding); // Use normalized version
        schedule.setMinCredits(minCredits);
        schedule.setMaxCredits(maxCredits);
        schedule.setStartDate(startDate);
        schedule.setEndDate(endDate);
        schedule.setDescription(description);
        
        return scheduleRepository.save(schedule);
    }
    
    /**
     * Delete a schedule
     */
    public void deleteSchedule(Long scheduleId) {
        if (!scheduleRepository.existsById(scheduleId)) {
            throw new RuntimeException("Schedule not found");
        }
        scheduleRepository.deleteById(scheduleId);
    }
    
    /**
     * Get all active schedules for a term on the current date
     */
    public List<RegistrationSchedule> getActiveSchedulesForTerm(String semester, Integer year) {
        LocalDate currentDate = systemDateService.today();
        return scheduleRepository.findActiveSchedulesForTerm(semester, year, currentDate);
    }
    
    /**
     * Build response map from schedule entity
     */
    private java.util.Map<String, Object> buildScheduleResponse(RegistrationSchedule schedule) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("scheduleId", schedule.getScheduleId());
        response.put("semester", schedule.getSemester());
        response.put("year", schedule.getYear());
        response.put("classStanding", schedule.getClassStanding());
        response.put("minCredits", schedule.getMinCredits());
        response.put("maxCredits", schedule.getMaxCredits());
        response.put("startDate", schedule.getStartDate());
        response.put("endDate", schedule.getEndDate());
        response.put("description", schedule.getDescription());
        if (schedule.getCreatedBy() != null) {
            response.put("createdBy", schedule.getCreatedBy().getUserId());
            response.put("createdByName", 
                schedule.getCreatedBy().getFirstName() + " " + schedule.getCreatedBy().getLastName());
        }
        response.put("createdAt", schedule.getCreatedAt());
        return response;
    }
}

