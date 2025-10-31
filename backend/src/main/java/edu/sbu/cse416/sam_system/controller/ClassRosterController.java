package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.ClassRoster;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.repository.ClassRosterRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Controller for managing class rosters and enrollment information.
 * Provides endpoints for viewing class rosters and enrollment statistics.
 * 
 * TP: Used Cursor to implement REST controller for class roster management.
 */
@RestController
@RequestMapping("/api/roster")
@CrossOrigin(origins = "http://localhost:5173")
public class ClassRosterController {
    
    @Autowired
    private ClassRosterRepository classRosterRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserClassesRepository userClassesRepository;
    
    /**
     * Get roster for a specific class
     * @param classId Class ID
     * @return List of enrolled students
     */
    @GetMapping("/class/{classId}")
    public ResponseEntity<List<Map<String, Object>>> getClassRoster(@PathVariable Long classId) {
        try {
            ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
            
            List<ClassRoster> roster = classRosterRepository.findActiveEnrollmentsByClass(classEntity);
            
            List<Map<String, Object>> rosterData = roster.stream()
                .map(rosterItem -> {
                    Map<String, Object> studentInfo = new HashMap<>();
                    studentInfo.put("rosterId", rosterItem.getRosterId());
                    studentInfo.put("userId", rosterItem.getUser().getUserId());
                    studentInfo.put("firstName", rosterItem.getUser().getFirstName());
                    studentInfo.put("lastName", rosterItem.getUser().getLastName());
                    studentInfo.put("email", rosterItem.getUser().getEmail());
                    studentInfo.put("enrollmentDate", rosterItem.getEnrollmentDate());
                    studentInfo.put("status", rosterItem.getStatus());
                    return studentInfo;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(rosterData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get all classes a student is enrolled in
     * @param userId User ID
     * @return List of enrolled classes
     */
    @GetMapping("/student/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getStudentEnrollments(@PathVariable Long userId) {
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<ClassRoster> enrollments = classRosterRepository.findActiveEnrollmentsByUser(user);
            
            List<Map<String, Object>> enrollmentData = enrollments.stream()
                .map(enrollment -> {
                    Map<String, Object> classInfo = new HashMap<>();
                    ClassEntity classEntity = enrollment.getClassEntity();
                    classInfo.put("rosterId", enrollment.getRosterId());
                    classInfo.put("classId", classEntity.getClassId());
                    classInfo.put("department", classEntity.getDepartment());
                    classInfo.put("courseNum", classEntity.getCourseNum());
                    classInfo.put("section", classEntity.getSection());
                    classInfo.put("title", classEntity.getTitle());
                    classInfo.put("instructor", classEntity.getInstructor());
                    classInfo.put("schedule", classEntity.getSchedule());
                    classInfo.put("location", classEntity.getLocation());
                    classInfo.put("credits", classEntity.getMinCredits());
                    classInfo.put("semester", classEntity.getSemester());
                    classInfo.put("year", classEntity.getYear());
                    classInfo.put("enrollmentDate", enrollment.getEnrollmentDate());
                    classInfo.put("status", enrollment.getStatus());
                    return classInfo;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(enrollmentData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get enrollment statistics for a class
     * @param classId Class ID
     * @return Enrollment statistics
     */
    @GetMapping("/stats/{classId}")
    public ResponseEntity<Map<String, Object>> getClassEnrollmentStats(@PathVariable Long classId) {
        try {
            ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
            
            Long activeEnrollments = classRosterRepository.countActiveEnrollmentsByClass(classEntity);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("classId", classId);
            stats.put("department", classEntity.getDepartment());
            stats.put("courseNum", classEntity.getCourseNum());
            stats.put("section", classEntity.getSection());
            stats.put("title", classEntity.getTitle());
            stats.put("maxCapacity", classEntity.getMaxCapacity());
            stats.put("currentCapacity", classEntity.getCurrentCapacity());
            stats.put("activeEnrollments", activeEnrollments);
            stats.put("availableSpots", classEntity.getAvailableSpots());
            stats.put("isFull", classEntity.isFull());
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Assign grade to a student for a specific class (by instructor)
     * POST /api/roster/class/{classId}/student/{studentId}/grade
     * Body: { "instructorId": 10, "grade": "A-" }
     */
    @PostMapping("/class/{classId}/student/{studentId}/grade")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> assignGrade(
            @PathVariable Long classId,
            @PathVariable Long studentId,
            @RequestBody Map<String, Object> body
    ) {
        try {
            // Validate input
            if (body == null || !body.containsKey("instructorId") || !body.containsKey("grade")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "instructorId and grade are required"
                ));
            }

            Long instructorId = Long.parseLong(body.get("instructorId").toString());
            String grade = String.valueOf(body.get("grade")).trim();
            if (grade.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "grade cannot be empty"
                ));
            }

            // Load entities
            User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new RuntimeException("Instructor not found"));
            if (instructor.getRole() != UserRole.INSTRUCTOR) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Only instructors can assign grades"
                ));
            }

            ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
            User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

            // Optional: verify instructor teaches this class by name match
            if (classEntity.getInstructor() != null) {
                String classInstr = classEntity.getInstructor().trim().toLowerCase();
                // If class has instructor string and it doesn't contain instructor's last name, block
                if (!classInstr.isEmpty() && !classInstr.contains(instructor.getLastName().toLowerCase())) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Instructor is not assigned to this class"
                    ));
                }
            }

            // Verify student is enrolled
            boolean enrolled = classRosterRepository.isStudentEnrolled(classEntity, student);
            if (!enrolled) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Student is not enrolled in this class"
                ));
            }

            // Update grade in UserClasses
            java.util.Optional<UserClasses> ucOpt = userClassesRepository.findByUserIdAndClassIdOptional(studentId, classId);
            if (ucOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "UserClasses record not found for this student/class"
                ));
            }
            UserClasses uc = ucOpt.get();
            uc.setGrade(grade);
            userClassesRepository.save(uc);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Grade assigned successfully",
                "classId", classId,
                "studentId", studentId,
                "grade", grade
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to assign grade: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Check if a student is enrolled in a specific class
     * @param userId User ID
     * @param classId Class ID
     * @return Enrollment status
     */
    @GetMapping("/check/{userId}/{classId}")
    public ResponseEntity<Map<String, Object>> checkEnrollment(@PathVariable Long userId, @PathVariable Long classId) {
        try {
            ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            boolean isEnrolled = classRosterRepository.isStudentEnrolled(classEntity, user);
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("classId", classId);
            result.put("isEnrolled", isEnrolled);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
}
