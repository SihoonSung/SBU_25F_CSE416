package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.CartEntity;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.entity.ClassRoster;
import edu.sbu.cse416.sam_system.repository.CartRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.ClassRosterRepository;
import edu.sbu.cse416.sam_system.service.PrerequisitesValidationService;
import edu.sbu.cse416.sam_system.entity.WaitlistEntry;
import edu.sbu.cse416.sam_system.repository.WaitlistRepository;
import edu.sbu.cse416.sam_system.util.TimeConflictChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Service class for cart functionality.
 * Handles business logic for cart operations including conflict detection.
 * 
 * Features:
 * - Add/remove classes from cart
 * - Conflict detection and status updates
 * - Cart management with foreign key relationships
 * 
 * TP: Used Cursor to implement cart service with conflict detection.
 */
@Service
@Transactional
public class CartService {
    
    @Autowired
    private CartRepository cartRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserClassesRepository userClassesRepository;
    
    @Autowired
    private ClassRosterRepository classRosterRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;
    
    @Autowired
    private PrerequisitesValidationService prerequisitesValidationService;
    
    /**
     * Add a class to user's cart
     * @param userId User ID
     * @param classId Class ID
     * @return CartEntity if successful, null if class already exists
     */
    public CartEntity addToCart(Long userId, Long classId) {
        // Check if class already exists in cart
        if (cartRepository.existsByUserIdAndClassId(userId, classId)) {
            return null; // Already exists
        }
        
        // Get class entity
        ClassEntity classEntity = classRepository.findById(classId).orElse(null);
        if (classEntity == null) {
            return null; // Class not found
        }
        
        // Default status
        String status = "Added";

        // If class is full, mark cart item as Waitlist (등록 시 대기자 등록 예정)
        if (classEntity.isFull()) {
            status = "Waitlist";
        }

        // Check prerequisites
        String prerequisites = classEntity.getPrerequisites();
        
        if (prerequisites != null && !prerequisites.trim().isEmpty() && !prerequisites.trim().equalsIgnoreCase("None")) {
            boolean prerequisitesMet = prerequisitesValidationService.validatePrerequisites(userId, prerequisites);
            if (!prerequisitesMet) {
                status = "Prerequisites";
                System.out.println("    [CART] Prerequisites not met for class " + classEntity.getDepartment() + " " + classEntity.getCourseNum());
            }
        }
        
        // Check antirequisites
        String antirequisites = classEntity.getAntirequisites();
        if (antirequisites != null && !antirequisites.trim().isEmpty()) {
            boolean antirequisitesOk = prerequisitesValidationService.validateAntirequisites(userId, antirequisites);
            if (!antirequisitesOk) {
                status = "Antirequisites";
                System.out.println("    [CART] Antirequisites conflict for class " + classEntity.getDepartment() + " " + classEntity.getCourseNum());
            }
        }
        
        // Check corequisites
        String corequisites = classEntity.getCorequisites();
        if (corequisites != null && !corequisites.trim().isEmpty() && !corequisites.trim().equalsIgnoreCase("None")) {
            // For now, use current semester/year from the class entity
            String currentSemester = classEntity.getSemester();
            Integer currentYear = classEntity.getYear();
            boolean corequisitesOk = prerequisitesValidationService.validateCorequisites(userId, corequisites, prerequisites, currentSemester, currentYear);
            if (!corequisitesOk) {
                status = "Corequisites";
                System.out.println("    [CART] Corequisites not met for class " + classEntity.getDepartment() + " " + classEntity.getCourseNum());
            }
        }
        
        // Create cart entity with appropriate status
        CartEntity cartEntity = new CartEntity(userId, classEntity, status);
        cartEntity = cartRepository.save(cartEntity);
        
        // Check for conflicts and update status
        updateConflictStatus(userId);
        
        return cartEntity;
    }
    
    /**
     * Remove a class from user's cart
     * @param userId User ID
     * @param classId Class ID
     * @return true if successful, false otherwise
     */
    public boolean removeFromCart(Long userId, Long classId) {
        try {
            // Check if the class is in cart
            CartEntity cartItem = cartRepository.findByUserIdAndClassId(userId, classId);
            
            if (cartItem != null) {
                // Class is in cart, remove it
                cartRepository.deleteByUserIdAndClassId(userId, classId);
                // Update conflict status after removal
                updateConflictStatus(userId);
            } else {
                // Class not in cart, check if it's registered in UserClasses
                boolean existsInUserClasses = userClassesRepository.existsByUserIdAndClassId(userId, classId);
                if (existsInUserClasses) {
                    // Remove from UserClasses (registered class)
                    userClassesRepository.deleteByUserIdAndClassId(userId, classId);
                    
                    // Remove from ClassRoster and update capacity
                    ClassEntity classEntity = classRepository.findById(classId).orElse(null);
                    if (classEntity != null) {
                        User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                            // Remove from roster
                            classRosterRepository.deleteByClassEntityAndUser(classEntity, user);
                            
                            // Update capacity
                            if (classEntity.getCurrentCapacity() != null && classEntity.getCurrentCapacity() > 0) {
                                classEntity.setCurrentCapacity(classEntity.getCurrentCapacity() - 1);
                                classRepository.save(classEntity);
                            }
                        }
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error removing from cart: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get all cart items for a user
     * @param userId User ID
     * @return List of cart items with class information
     */
    public List<Map<String, Object>> getCartItems(Long userId) {
        List<CartEntity> cartItems = cartRepository.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (CartEntity cartItem : cartItems) {
            ClassEntity classEntity = cartItem.getClassEntity();
            Map<String, Object> item = new HashMap<>();
            
            // Basic cart info
            item.put("id", cartItem.getId());
            item.put("status", cartItem.getStatus());
            item.put("addedAt", cartItem.getAddedAt());
            
            // Class info
            item.put("classId", classEntity.getClassId());
            item.put("code", classEntity.getDepartment() + classEntity.getCourseNum() + "." + classEntity.getSection());
            item.put("title", classEntity.getTitle());
            item.put("schedule", classEntity.getSchedule());
            item.put("instructor", classEntity.getInstructor());
            item.put("location", classEntity.getLocation());
            item.put("lecSchedule", classEntity.getLecSchedule());
            item.put("lecLocation", classEntity.getLecLocation());
            item.put("lecInstructor", classEntity.getLecInstructor());
            item.put("credits", classEntity.getCreditsDisplay());
            
            result.add(item);
        }
        
        return result;
    }
    
    /**
     * Update conflict status for all cart items
     * @param userId User ID
     */
    public void updateConflictStatus(Long userId) {
        List<CartEntity> cartItems = cartRepository.findByUserId(userId);
        
        // Reset all statuses to "Added" first
        for (CartEntity cartItem : cartItems) {
            cartItem.setStatus("Added");
        }
        cartRepository.saveAll(cartItems);
        
        // Check for conflicts
        for (int i = 0; i < cartItems.size(); i++) {
            for (int j = i + 1; j < cartItems.size(); j++) {
                CartEntity item1 = cartItems.get(i);
                CartEntity item2 = cartItems.get(j);
                
                // Combine schedules for conflict checking
                String schedule1 = combineSchedules(item1.getClassEntity());
                String schedule2 = combineSchedules(item2.getClassEntity());
                
                if (schedule1 != null && schedule2 != null) {
                    boolean hasConflict = TimeConflictChecker.hasTimeConflict(schedule1, schedule2);
                    
                    if (hasConflict) {
                        // Update both items to Conflict status
                        cartRepository.updateStatusByUserIdAndClassId(userId, item1.getClassEntity().getClassId(), "Conflict");
                        cartRepository.updateStatusByUserIdAndClassId(userId, item2.getClassEntity().getClassId(), "Conflict");
                    }
                }
            }
        }
    }
    
    /**
     * Combine LEC and REC schedules into a single string
     * @param classEntity Class entity
     * @return Combined schedule string
     */
    private String combineSchedules(ClassEntity classEntity) {
        List<String> schedules = new ArrayList<>();
        if (classEntity.getLecSchedule() != null && !classEntity.getLecSchedule().trim().isEmpty()) {
            schedules.add(classEntity.getLecSchedule());
        }
        if (classEntity.getSchedule() != null && !classEntity.getSchedule().trim().isEmpty()) {
            schedules.add(classEntity.getSchedule());
        }
        return schedules.isEmpty() ? null : String.join(", ", schedules);
    }
    
    /**
     * Clear all cart items for a user
     * @param userId User ID
     * @return true if successful
     */
    public boolean clearCart(Long userId) {
        try {
            cartRepository.deleteByUserId(userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Submit registration for all cart items
     * @param userId User ID
     * @return Map with success status and details
     */
    public Map<String, Object> submitRegistration(Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get user
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Get all cart items for user
            List<CartEntity> cartItems = cartRepository.findByUserId(userId);
            
            if (cartItems.isEmpty()) {
                result.put("success", false);
                result.put("message", "Cart is empty");
                return result;
            }
            
            // Get already registered classes from UserClasses
            List<UserClasses> alreadyRegisteredClasses = userClassesRepository.findByUserId(userId);
            
            // Check prerequisites, antirequisites, and corequisites for all cart items
            List<String> prerequisiteErrors = new ArrayList<>();
            List<String> antirequisiteErrors = new ArrayList<>();
            List<String> corequisiteErrors = new ArrayList<>();
            
            for (CartEntity cartItem : cartItems) {
                if (cartItem.getStatus().equals("Added")) {
                    // Check prerequisites
                    String prerequisites = cartItem.getClassEntity().getPrerequisites();
                    if (prerequisites != null && !prerequisites.trim().isEmpty() && !prerequisites.trim().equalsIgnoreCase("None")) {
                        boolean prerequisitesMet = prerequisitesValidationService.validatePrerequisites(userId, prerequisites);
                        if (!prerequisitesMet) {
                            prerequisiteErrors.add(String.format("Prerequisites not met for %s %s: %s", 
                                cartItem.getClassEntity().getDepartment(), 
                                cartItem.getClassEntity().getCourseNum(),
                                prerequisites));
                        }
                    }
                    
                    // Check antirequisites
                    String antirequisites = cartItem.getClassEntity().getAntirequisites();
                    if (antirequisites != null && !antirequisites.trim().isEmpty()) {
                        boolean antirequisitesOk = prerequisitesValidationService.validateAntirequisites(userId, antirequisites);
                        if (!antirequisitesOk) {
                            antirequisiteErrors.add(String.format("Antirequisites conflict for %s %s: %s", 
                                cartItem.getClassEntity().getDepartment(), 
                                cartItem.getClassEntity().getCourseNum(),
                                antirequisites));
                        }
                    }
                    
                    // Check corequisites
                    String corequisites = cartItem.getClassEntity().getCorequisites();
                    if (corequisites != null && !corequisites.trim().isEmpty() && !corequisites.trim().equalsIgnoreCase("None")) {
                        String currentSemester = cartItem.getClassEntity().getSemester();
                        Integer currentYear = cartItem.getClassEntity().getYear();
                        boolean corequisitesOk = prerequisitesValidationService.validateCorequisites(userId, corequisites, prerequisites, currentSemester, currentYear);
                        if (!corequisitesOk) {
                            String corequisiteMessage = generateCorequisiteErrorMessage(cartItem.getClassEntity().getDepartment(), 
                                cartItem.getClassEntity().getCourseNum(), corequisites);
                            corequisiteErrors.add(corequisiteMessage);
                        }
                    }
                }
            }
            
            // If there are prerequisite errors, return them
            if (!prerequisiteErrors.isEmpty()) {
                result.put("success", false);
                result.put("prerequisiteErrors", prerequisiteErrors);
                return result;
            }
            
            // If there are antirequisite errors, return them
            if (!antirequisiteErrors.isEmpty()) {
                result.put("success", false);
                result.put("antirequisiteErrors", antirequisiteErrors);
                return result;
            }
            
            // If there are corequisite errors, return them
            if (!corequisiteErrors.isEmpty()) {
                result.put("success", false);
                result.put("corequisiteErrors", corequisiteErrors);
                return result;
            }
            
            // Check for conflicts among cart items
            List<String> conflicts = new ArrayList<>();
            for (int i = 0; i < cartItems.size(); i++) {
                for (int j = i + 1; j < cartItems.size(); j++) {
                    CartEntity item1 = cartItems.get(i);
                    CartEntity item2 = cartItems.get(j);
                    
                    if (item1.getStatus().equals("Added") && item2.getStatus().equals("Added")) {
                        String schedule1 = combineSchedules(item1.getClassEntity());
                        String schedule2 = combineSchedules(item2.getClassEntity());
                        
                        if (schedule1 != null && schedule2 != null) {
                            boolean hasConflict = TimeConflictChecker.hasTimeConflict(schedule1, schedule2);
                            if (hasConflict) {
                                conflicts.add(String.format("Conflict between %s and %s", 
                                    item1.getClassEntity().getDepartment() + item1.getClassEntity().getCourseNum(),
                                    item2.getClassEntity().getDepartment() + item2.getClassEntity().getCourseNum()));
                            }
                        }
                    }
                }
            }
            
            // Check for conflicts between cart items and already registered classes
            for (CartEntity cartItem : cartItems) {
                if (cartItem.getStatus().equals("Added")) {
                    String cartSchedule = combineSchedules(cartItem.getClassEntity());
                    
                    if (cartSchedule != null) {
                        for (UserClasses registeredClass : alreadyRegisteredClasses) {
                            // Get the original class entity for the registered class
                            ClassEntity registeredClassEntity = classRepository.findById(registeredClass.getClassId()).orElse(null);
                            
                            if (registeredClassEntity != null) {
                                String registeredSchedule = combineSchedules(registeredClassEntity);
                                
                                if (registeredSchedule != null) {
                                    boolean hasConflict = TimeConflictChecker.hasTimeConflict(cartSchedule, registeredSchedule);
                                    if (hasConflict) {
                                        conflicts.add(String.format("Conflict between cart item %s and already registered %s %s", 
                                            cartItem.getClassEntity().getDepartment() + cartItem.getClassEntity().getCourseNum(),
                                            registeredClassEntity.getDepartment(), 
                                            registeredClassEntity.getCourseNum()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Check for duplicate classes already registered in UserClasses
            List<String> duplicateClasses = new ArrayList<>();
            for (CartEntity cartItem : cartItems) {
                if (cartItem.getStatus().equals("Added")) {
                    ClassEntity classEntity = cartItem.getClassEntity();
                    
                    // Check if this class is already registered in UserClasses for the same semester/year
                    boolean alreadyRegistered = userClassesRepository.existsByUserIdAndClassId(userId, classEntity.getClassId());
                    
                    if (alreadyRegistered) {
                        duplicateClasses.add(String.format("%s %s (Section %s) - %s %d", 
                            classEntity.getDepartment(), 
                            classEntity.getCourseNum(),
                            classEntity.getSection(),
                            classEntity.getSemester(), 
                            classEntity.getYear()));
                    }
                }
            }
            
            // If conflicts exist, return error
            if (!conflicts.isEmpty()) {
                result.put("success", false);
                result.put("message", "Time conflicts detected");
                result.put("conflicts", conflicts);
                return result;
            }
            
            // If duplicate classes exist, return error
            if (!duplicateClasses.isEmpty()) {
                result.put("success", false);
                result.put("message", "Some classes are already registered");
                result.put("duplicateClasses", duplicateClasses);
                return result;
            }
            
            // Register all non-conflict cart items and remove them from cart
            List<UserClasses> registeredClasses = new ArrayList<>();
            List<Map<String, Object>> waitlistedClasses = new ArrayList<>();
            List<Long> cartItemsToDelete = new ArrayList<>();
            
            for (CartEntity cartItem : cartItems) {
                if (cartItem.getStatus().equals("Added") || cartItem.getStatus().equals("Waitlist")) {
                    ClassEntity classEntity = cartItem.getClassEntity();
                    
                    // If class is full, add to waitlist instead of registering
                    if (classEntity.isFull()) {
                        if (classEntity.getCurrentCapacity() == null) {
                            classEntity.setCurrentCapacity(0);
                        }

                        // Avoid duplicate waitlist entries
                        if (!waitlistRepository.existsByClassEntityAndUser(classEntity, user)) {
                            WaitlistEntry entry = new WaitlistEntry(classEntity, user);
                            waitlistRepository.save(entry);
                        }

                        Map<String, Object> wl = new HashMap<>();
                        wl.put("classId", classEntity.getClassId());
                        wl.put("department", classEntity.getDepartment());
                        wl.put("courseNum", classEntity.getCourseNum());
                        wl.put("title", classEntity.getTitle());
                        wl.put("position", waitlistRepository.countByClassEntity(classEntity));
                        waitlistedClasses.add(wl);

                        // Remove from cart regardless
                        cartItemsToDelete.add(cartItem.getId());
                        continue;
                    }

                    // Create UserClasses entry
                    UserClasses userClass = new UserClasses();
                    userClass.setUser(user);
                    userClass.setClassId(classEntity.getClassId());
                    userClass.setDepartment(classEntity.getDepartment());
                    userClass.setCourseNum(classEntity.getCourseNum());
                    userClass.setSection(classEntity.getSection());
                    userClass.setSemester(classEntity.getSemester());
                    userClass.setYear(classEntity.getYear());
                    userClass.setCredits(classEntity.getMinCredits());
                    userClass.setGpnc(null); // Default to null for new registrations
                    userClass.setGrade(null); // Default to null for new registrations
                    
                    // Create ClassRoster entry
                    ClassRoster roster = new ClassRoster(classEntity, user);
                    
                    // Update class capacity
                    if (classEntity.getCurrentCapacity() == null) {
                        classEntity.setCurrentCapacity(0);
                    }
                    classEntity.setCurrentCapacity(classEntity.getCurrentCapacity() + 1);
                    
                    UserClasses saved = userClassesRepository.save(userClass);
                    classRosterRepository.save(roster);
                    classRepository.save(classEntity);
                    registeredClasses.add(saved);
                    
                    // Mark cart item for deletion (remove from cart completely)
                    cartItemsToDelete.add(cartItem.getId());
                }
            }
            
            // Delete registered items from cart
            for (Long cartItemId : cartItemsToDelete) {
                cartRepository.deleteById(cartItemId);
            }
            
            result.put("success", true);
            result.put("registeredCount", registeredClasses.size());
            result.put("registeredClasses", registeredClasses);
            if (!waitlistedClasses.isEmpty()) {
                result.put("waitlistedCount", waitlistedClasses.size());
                result.put("waitlistedClasses", waitlistedClasses);

                // Build human-readable message for waitlist positions
                StringBuilder wb = new StringBuilder();
                if (registeredClasses.isEmpty()) {
                    wb.append("0 classes registered. ");
                }
                wb.append("Added to waitlist: ");
                for (int i = 0; i < waitlistedClasses.size(); i++) {
                    Map<String, Object> wl = waitlistedClasses.get(i);
                    String dept = String.valueOf(wl.get("department"));
                    String num = String.valueOf(wl.get("courseNum"));
                    Object pos = wl.get("position");
                    if (i > 0) wb.append(", ");
                    wb.append(dept).append(" ").append(num).append(" (position ").append(pos).append(")");
                }
                result.put("message", wb.toString());
            } else {
                result.put("message", "Registration processed");
            }
            
        } catch (Exception e) {
            System.err.println("Error submitting registration: " + e.getMessage());
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "Registration failed: " + e.getMessage());
        }
        
        return result;
    }
    
    // Pattern for matching course codes in corequisite error messages
    private static final java.util.regex.Pattern COURSE_PATTERN = java.util.regex.Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");
    
    /**
     * Generate detailed corequisite error message with specific course recommendations
     */
    private String generateCorequisiteErrorMessage(String department, Integer courseNum, String corequisites) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("Corequisites not met for %s %s: %s\n", department, courseNum, corequisites));
        message.append("Please register for one of the following courses first:\n");
        
        // Parse corequisites to extract specific course recommendations
        String[] corequisiteGroups = corequisites.split(";");
        for (String group : corequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            // Check for range patterns like "MUS 161 through 187"
            if (group.toLowerCase().contains("through")) {
                // Extract range and suggest specific courses
                String[] parts = group.split("\\s+through\\s+");
                if (parts.length == 2) {
                    String[] firstPart = parts[0].trim().split("\\s+");
                    if (firstPart.length >= 2) {
                        String dept = firstPart[firstPart.length - 2];
                        int startCourse = Integer.parseInt(firstPart[firstPart.length - 1]);
                        int endCourse = Integer.parseInt(parts[1].trim());
                        
                        message.append(String.format("- Any %s course from %s %d to %s %d\n", 
                            dept, dept, startCourse, dept, endCourse));
                    }
                }
            } else {
                // Extract individual course codes
                java.util.regex.Matcher matcher = COURSE_PATTERN.matcher(group);
                while (matcher.find()) {
                    String courseDept = matcher.group(1);
                    String courseNumber = matcher.group(2);
                    message.append(String.format("- %s %s\n", courseDept, courseNumber));
                }
            }
        }
        
        return message.toString().trim();
    }
}
