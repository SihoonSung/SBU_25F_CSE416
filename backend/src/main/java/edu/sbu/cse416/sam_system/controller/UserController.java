package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.dto.StudentProfileDto;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.entity.UserTransferCourses;
import edu.sbu.cse416.sam_system.entity.UserCoursePlan;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.ClassRoster;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.UserTransferCoursesRepository;
import edu.sbu.cse416.sam_system.repository.UserCoursePlanRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import edu.sbu.cse416.sam_system.repository.ClassRosterRepository;
import edu.sbu.cse416.sam_system.service.UserService;
import edu.sbu.cse416.sam_system.service.AuditLogService;
import edu.sbu.cse416.sam_system.entity.WaitlistEntry;
import edu.sbu.cse416.sam_system.repository.WaitlistRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * TP: Used cursor to implement REST controller for user management.
 * Handles user lookup, registration, and profile management endpoints.
 */
@CrossOrigin(
    origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://localhost:5175"},
    allowCredentials = "true"
)
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserClassesRepository userClassesRepository;
    private final UserTransferCoursesRepository userTransferCoursesRepository;
    private final UserCoursePlanRepository userCoursePlanRepository;
    private final ClassRepository classRepository;
    private final ClassRosterRepository classRosterRepository;
    private final WaitlistRepository waitlistRepository;
    private final AuditLogService auditLogService;

    public UserController(UserRepository userRepository, UserService userService,
                         UserClassesRepository userClassesRepository,
                         UserTransferCoursesRepository userTransferCoursesRepository,
                         UserCoursePlanRepository userCoursePlanRepository,
                         ClassRepository classRepository,
                         ClassRosterRepository classRosterRepository,
                         WaitlistRepository waitlistRepository,
                         AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userClassesRepository = userClassesRepository;
        this.userTransferCoursesRepository = userTransferCoursesRepository;
        this.userCoursePlanRepository = userCoursePlanRepository;
        this.classRepository = classRepository;
        this.classRosterRepository = classRosterRepository;
        this.waitlistRepository = waitlistRepository;
        this.auditLogService = auditLogService;
    }

    /** Test endpoint to verify controller is loaded */
    @GetMapping("/test")
    // TP: Used cursor to verify user controller is active
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("UserController is working!");
    }


    /**
     * Lookup by email or studentId from users table only.
     *   /api/users/student-lookup?email=abc@xyz.com
     *   /api/users/student-lookup?studentId=1100123456
     * If both are provided, email wins.
     */
    @GetMapping("/student-lookup")
    @PreAuthorize("permitAll()")
    // TP: Used cursor to lookup student by email or studentId
    public StudentProfileDto lookupStudent(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "studentId", required = false) String studentId
    ) {
        Optional<User> u;
        if (email != null && !email.isBlank()) {
            u = userRepository.findByEmailIgnoreCase(email.trim());
        } else if (studentId != null && !studentId.isBlank()) {
            u = userRepository.findByStudentId(studentId.trim());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email or studentId is required");
        }

        User user = u.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        return toStudentProfileDto(user);
    }

    // ---------- mapper ----------
    // TP: Used cursor to map User to StudentProfileDto
    private static StudentProfileDto toStudentProfileDto(User u) {
        StudentProfileDto d = new StudentProfileDto();

        // internal id: try getId() -> getUserId() -> getUid(); else 0L
        d.id = safeInternalId(u);

        d.studentId = nvl(callString(u, "getStudentId"), "-");

        String first = nvl(callString(u, "getFirstName"), "");
        String last  = nvl(callString(u, "getLastName"), "");
        String name = (first + " " + last).trim();
        d.name = name.isEmpty() ? "-" : name;

        d.major = nvl(callString(u, "getMajor"), "-");
        d.email = nvl(callString(u, "getEmail"), "-");

        // Not present on User -> default values
        d.status = "-";
        d.entryYear = "-";

        return d;
    }

    // ---------- helpers (no dependency on missing getters) ----------
    // TP: Used cursor to safely derive internal id from various getter names
    private static Long safeInternalId(User u) {
        Long v = callLong(u, "getId");
        if (v != null) return v;
        v = callLong(u, "getUserId");
        if (v != null) return v;
        v = callLong(u, "getUid");
        if (v != null) return v;
        return 0L;
    }

    // TP: Used cursor to provide null/blank default value helper
    private static String nvl(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    // TP: Used cursor to reflectively call string getter
    private static String callString(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            Object out = m.invoke(target);
            return (out == null) ? null : String.valueOf(out);
        } catch (Exception ignore) {
            return null;
        }
    }

    // TP: Used cursor to reflectively call long/number getter
    private static Long callLong(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            Object out = m.invoke(target);
            if (out == null) return null;
            if (out instanceof Long) return (Long) out;
            if (out instanceof Number) return ((Number) out).longValue();
            String s = String.valueOf(out);
            return s.matches("\\d+") ? Long.parseLong(s) : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    // ================= Registrar/Admin APIs =================

    /**
     * Get all users (for registrar)
     */
    @GetMapping
    // TP: Used cursor to list all users (registrar)
    public ResponseEntity<List<User>> getAllUsers(
            @AuthenticationPrincipal OAuth2User principal) {
        
        // Temporarily disable authorization for testing
        // if (!isRegistrar(principal)) {
        //     return ResponseEntity.status(403).build();
        // }
        
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /** Activate user */
    @PutMapping("/{userId}/activate")
    // TP: Used cursor to activate a user
    public ResponseEntity<User> activateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }

        try {
            User user = userService.activateUser(userId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Deactivate user */
    @PutMapping("/{userId}/deactivate")
    // TP: Used cursor to deactivate a user
    public ResponseEntity<User> deactivateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }

        try {
            User user = userService.deactivateUser(userId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Import users from YAML file */
    @PostMapping("/import")
    // TP: Used cursor to import users from YAML upload
    public ResponseEntity<Map<String, Object>> importUsers(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal OAuth2User principal) {

        // Temporarily disable authorization for testing
        // if (!isRegistrar(principal)) {
        //     return ResponseEntity.status(403).build();
        // }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            List<User> importedUsers = userService.importUsersFromYaml(file);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("importedCount", importedUsers.size());
            response.put("users", importedUsers);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }

    /** Get user statistics */
    @GetMapping("/statistics")
    // TP: Used cursor to provide user statistics
    public ResponseEntity<Map<String, Object>> getUserStatistics(
            @AuthenticationPrincipal OAuth2User principal) {

        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> stats = userService.getUserStatistics();
        return ResponseEntity.ok(stats);
    }

    /** Search users with multiple filters (for registrars and advisors) */
    @GetMapping("/search")
    // TP: Used cursor to search users with multiple filters
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "major", required = false) String major,
            @RequestParam(value = "minor", required = false) String minor,
            @RequestParam(value = "graduationTerm", required = false) String graduationTerm,
            @RequestParam(value = "advisorLevel", required = false) String advisorLevel,
            @RequestParam(value = "academicUnit", required = false) String academicUnit,
            @AuthenticationPrincipal OAuth2User principal) {
        
        // Temporarily disable authorization for testing
        // if (!isRegistrar(principal) && !isAdvisor(principal)) {
        //     return ResponseEntity.status(403).build();
        // }
        
        List<User> users = userService.searchUsers(name, role, major, minor, 
                                                 graduationTerm, advisorLevel, academicUnit);
        return ResponseEntity.ok(users);
    }

    /** Export user data as YAML (for registrars only) */
    @GetMapping("/{userId}/export")
    // TP: Used cursor to export a user's data in YAML-like structure
    public ResponseEntity<Map<String, Object>> exportUserData(
            @PathVariable Long userId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        try {
            Map<String, Object> userData = userService.exportUserToYamlFormat(userId);
            if (userData == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(userData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to export user data: " + e.getMessage()));
        }
    }

    /** Check if current user is registrar */
    // TP: Used cursor to check registrar role
    private boolean isRegistrar(OAuth2User principal) {
        if (principal == null) return false;
        String googleId = principal.getName();
        var userOpt = userService.getUserByGoogleId(googleId);
        return userOpt.isPresent() && userOpt.get().isRegistrar();
    }

    /** Check if current user is advisor */
    // TP: Used cursor to check advisor role
    private boolean isAdvisor(OAuth2User principal) {
        if (principal == null) return false;
        String googleId = principal.getName();
        var userOpt = userService.getUserByGoogleId(googleId);
        return userOpt.isPresent() && userOpt.get().isAdvisor();
    }

    // ===== New Normalized Table APIs =====

    /** Get user's classes */
    @GetMapping("/{userId}/classes")
    // TP: Used cursor to fetch user's classes
    public ResponseEntity<List<UserClasses>> getUserClasses(@PathVariable Long userId) {
        try {
            List<UserClasses> classes = userClassesRepository.findByUserId(userId);
            return ResponseEntity.ok(classes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Get user's transfer courses */
    @GetMapping("/{userId}/transfer-courses")
    // TP: Used cursor to fetch user's transfer courses
    public ResponseEntity<List<UserTransferCourses>> getUserTransferCourses(@PathVariable Long userId) {
        try {
            List<UserTransferCourses> transferCourses = userTransferCoursesRepository.findByUserId(userId);
            return ResponseEntity.ok(transferCourses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Get user's course plan */
    @GetMapping("/{userId}/course-plan")
    // TP: Used cursor to fetch user's course plan
    public ResponseEntity<List<UserCoursePlan>> getUserCoursePlan(@PathVariable Long userId) {
        try {
            List<UserCoursePlan> coursePlan = userCoursePlanRepository.findByUserId(userId);
            return ResponseEntity.ok(coursePlan);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Get user's classes by semester and year */
    @GetMapping("/{userId}/classes/{semester}/{year}")
    // TP: Used cursor to fetch user's classes by semester/year
    public ResponseEntity<List<UserClasses>> getUserClassesBySemesterAndYear(
            @PathVariable Long userId, 
            @PathVariable String semester, @PathVariable Integer year) {
        try {
            List<UserClasses> classes = userClassesRepository.findByUserIdAndSemesterAndYear(userId, semester, year);
            return ResponseEntity.ok(classes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /** Get user's completed classes (with grades) */
    @GetMapping("/{userId}/classes/completed")
    // TP: Used cursor to fetch user's completed classes
    public ResponseEntity<List<UserClasses>> getCompletedClasses(@PathVariable Long userId) {
        try {
            List<UserClasses> completedClasses = userClassesRepository.findCompletedClassesByUserId(userId);
            return ResponseEntity.ok(completedClasses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Get user's future classes (without grades) */
    @GetMapping("/{userId}/classes/future")
    // TP: Used cursor to fetch user's future classes
    public ResponseEntity<List<UserClasses>> getFutureClasses(@PathVariable Long userId) {
        try {
            List<UserClasses> futureClasses = userClassesRepository.findFutureClassesByUserId(userId);
            return ResponseEntity.ok(futureClasses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Drop a class for a user (delete from UserClasses and ClassRoster) */
    @PostMapping("/{userId}/classes/{classId}/drop")
    @Transactional
    // TP: Used cursor to drop a class and process waitlist
    public ResponseEntity<Map<String, Object>> dropClass(@PathVariable Long userId, @PathVariable Long classId) {
        try {
            // Check if user exists
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Check if class exists
            ClassEntity classEntity = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));
            
            // Check if user is enrolled in this class
            if (!userClassesRepository.existsByUserIdAndClassId(userId, classId)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "User is not enrolled in this class"
                ));
            }
            
            // Check if this is a current semester class (can only drop current semester classes)
            // Get current semester/year from the class entity
            String currentSemester = classEntity.getSemester();
            Integer currentYear = classEntity.getYear();
            
            // Check if this class is from current semester
            List<UserClasses> currentSemesterClasses = userClassesRepository
                .findByUserIdAndSemesterAndYear(userId, currentSemester, currentYear);
            
            boolean isCurrentSemesterClass = currentSemesterClasses.stream()
                .anyMatch(uc -> uc.getClassId().equals(classId));
            
            if (!isCurrentSemesterClass) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Can only drop classes from current semester"
                ));
            }
            
            // Log audit entry before deletion
            String className = classEntity.getDepartment() + " " + classEntity.getCourseNum();
            auditLogService.logClassDropped(user, user, className, null);
            
            // Delete from UserClasses
            userClassesRepository.deleteByUserIdAndClassId(userId, classId);
            
            // Delete from ClassRoster
            classRosterRepository.deleteByClassEntityAndUser(classEntity, user);
            
            // Update class capacity
            if (classEntity.getCurrentCapacity() != null && classEntity.getCurrentCapacity() > 0) {
                classEntity.setCurrentCapacity(classEntity.getCurrentCapacity() - 1);
                classRepository.save(classEntity);
            }

            // Ensure any waitlist entry for this user/class is removed (safety)
            try {
                waitlistRepository.deleteByClassEntityAndUser(classEntity, user);
            } catch (Exception ignore) {}

            // Auto-enroll next waitlisted student if any
            try {
                Optional<WaitlistEntry> next = waitlistRepository.findFirstByClassEntityOrderByCreatedAtAsc(classEntity);
                if (next.isPresent() && !classEntity.isFull()) {
                    WaitlistEntry wl = next.get();
                    User nextUser = wl.getUser();

                    // Create UserClasses
                    UserClasses userClass = new UserClasses();
                    userClass.setUser(nextUser);
                    userClass.setClassId(classEntity.getClassId());
                    userClass.setDepartment(classEntity.getDepartment());
                    userClass.setCourseNum(classEntity.getCourseNum());
                    userClass.setSection(classEntity.getSection());
                    userClass.setSemester(classEntity.getSemester());
                    userClass.setYear(classEntity.getYear());
                    userClass.setCredits(classEntity.getMinCredits());
                    userClass.setGpnc(null);
                    userClass.setGrade("I"); // Default to "I" (Incomplete) for new registrations

                    // Create roster
                    ClassRoster roster = new ClassRoster(classEntity, nextUser);

                    if (classEntity.getCurrentCapacity() == null) {
                        classEntity.setCurrentCapacity(0);
                    }
                    classEntity.setCurrentCapacity(classEntity.getCurrentCapacity() + 1);

                    userClassesRepository.save(userClass);
                    classRosterRepository.save(roster);
                    classRepository.save(classEntity);

                    // Mark waitlist entry and remove
                    wl.setStatus("ENROLLED");
                    waitlistRepository.deleteByClassEntityAndUser(classEntity, nextUser);
                }
            } catch (Exception ignore) {
                // best-effort; do not fail drop if waitlist processing fails
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Class dropped successfully",
                "classId", classId,
                "department", classEntity.getDepartment(),
                "courseNum", classEntity.getCourseNum(),
                "title", classEntity.getTitle()
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to drop class: " + e.getMessage()
            ));
        }
    }

    /** Cancel waitlist for a class (remove from waitlist without affecting enrollment) */
    @PostMapping("/{userId}/classes/{classId}/waitlist/cancel")
    @Transactional
    // TP: Used cursor to cancel a waitlist entry for a class
    public ResponseEntity<Map<String, Object>> cancelWaitlist(
            @PathVariable Long userId,
            @PathVariable Long classId
    ) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            ClassEntity classEntity = classRepository.findById(classId)
                    .orElseThrow(() -> new RuntimeException("Class not found"));

            boolean exists = waitlistRepository.existsByClassEntityAndUser(classEntity, user);
            if (!exists) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "User is not on the waitlist for this class"
                ));
            }

            waitlistRepository.deleteByClassEntityAndUser(classEntity, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Waitlist entry removed",
                    "classId", classId,
                    "department", classEntity.getDepartment(),
                    "courseNum", classEntity.getCourseNum(),
                    "title", classEntity.getTitle()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to cancel waitlist: " + e.getMessage()
            ));
        }
    }
}