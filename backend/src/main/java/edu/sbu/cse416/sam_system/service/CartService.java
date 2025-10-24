package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.CartEntity;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.repository.CartRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
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
            cartRepository.deleteByUserIdAndClassId(userId, classId);
            // Update conflict status after removal
            updateConflictStatus(userId);
            return true;
        } catch (Exception e) {
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
}
