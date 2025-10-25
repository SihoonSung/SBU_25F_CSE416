package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.CartEntity;
import edu.sbu.cse416.sam_system.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for cart functionality.
 * Provides API endpoints for cart operations.
 * 
 * Features:
 * - Add/remove classes from cart
 * - Get cart items
 * - Conflict detection and status updates
 * - CORS support for frontend integration
 * 
 * TP: Used Cursor to implement REST API for cart functionality.
 */
@RestController
@RequestMapping("/api/cart")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CartController {
    
    @Autowired
    private CartService cartService;
    
    /**
     * Add a class to user's cart
     * POST /api/cart/add
     * Body: {"userId": 1, "classId": 12345}
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addToCart(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // null 체크
            Object userIdObj = request.get("userId");
            Object classIdObj = request.get("classId");
            
            if (userIdObj == null || classIdObj == null) {
                response.put("success", false);
                response.put("message", "userId and classId are required");
                return ResponseEntity.badRequest().body(response);
            }
            
            Long userId = Long.valueOf(userIdObj.toString());
            Long classId = Long.valueOf(classIdObj.toString());
            
            CartEntity cartEntity = cartService.addToCart(userId, classId);
            
            if (cartEntity != null) {
                response.put("success", true);
                response.put("message", "Class added to cart successfully");
                response.put("cartItem", cartEntity);
            } else {
                response.put("success", false);
                response.put("message", "Class already exists in cart or not found");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error adding class to cart: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Remove a class from user's cart
     * DELETE /api/cart/remove?userId=1&classId=12345
     */
    @DeleteMapping("/remove")
    public ResponseEntity<Map<String, Object>> removeFromCart(
            @RequestParam Long userId, 
            @RequestParam Long classId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = cartService.removeFromCart(userId, classId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "Class removed from cart successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to remove class from cart");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error removing class from cart: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all cart items for a user
     * GET /api/cart/items?userId=1
     */
    @GetMapping("/items")
    public ResponseEntity<Map<String, Object>> getCartItems(@RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> cartItems = cartService.getCartItems(userId);
            
            response.put("success", true);
            response.put("items", cartItems);
            response.put("count", cartItems.size());
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching cart items: " + e.getMessage());
            response.put("items", List.of());
            response.put("count", 0);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update conflict status for all cart items
     * POST /api/cart/update-conflicts?userId=1
     */
    @PostMapping("/update-conflicts")
    public ResponseEntity<Map<String, Object>> updateConflicts(@RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            cartService.updateConflictStatus(userId);
            response.put("success", true);
            response.put("message", "Conflict status updated successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating conflicts: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Clear all cart items for a user
     * DELETE /api/cart/clear?userId=1
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearCart(@RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = cartService.clearCart(userId);
            
            if (success) {
                response.put("success", true);
                response.put("message", "Cart cleared successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to clear cart");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error clearing cart: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Submit registration for all cart items
     * @param userId User ID
     * @return Registration result with success status and details
     */
    @PostMapping("/submit-registration")
    public ResponseEntity<Map<String, Object>> submitRegistration(@RequestParam Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> result = cartService.submitRegistration(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error submitting registration: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
}
