package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.CartEntity;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.repository.CartRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
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
        
        // Create cart entity with "Added" status
        CartEntity cartEntity = new CartEntity(userId, classEntity, "Added");
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
            
            // Check for conflicts among cart items
            List<String> conflicts = new ArrayList<>();
            for (int i = 0; i < cartItems.size(); i++) {
                for (int j = i + 1; j < cartItems.size(); j++) {
                    CartEntity item1 = cartItems.get(i);
                    CartEntity item2 = cartItems.get(j);
                    
                    if (item1.getStatus().equals("Added") && item2.getStatus().equals("Added")) {
                        String schedule1 = item1.getClassEntity().getSchedule();
                        String schedule2 = item2.getClassEntity().getSchedule();
                        
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
            List<Long> cartItemsToDelete = new ArrayList<>();
            
            for (CartEntity cartItem : cartItems) {
                if (cartItem.getStatus().equals("Added")) {
                    ClassEntity classEntity = cartItem.getClassEntity();
                    
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
                    
                    UserClasses saved = userClassesRepository.save(userClass);
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
            result.put("message", "Registration successful");
            result.put("registeredCount", registeredClasses.size());
            result.put("registeredClasses", registeredClasses);
            
        } catch (Exception e) {
            System.err.println("Error submitting registration: " + e.getMessage());
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "Registration failed: " + e.getMessage());
        }
        
        return result;
    }
}
