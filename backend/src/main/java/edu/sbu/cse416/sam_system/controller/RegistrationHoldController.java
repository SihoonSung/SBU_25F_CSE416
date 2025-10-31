package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.RegistrationHold;
import edu.sbu.cse416.sam_system.service.RegistrationHoldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for registration hold management.
 * 
 * Features:
 * - Place registration holds
 * - Remove registration holds
 * - Check student holds
 * - List holds for a student
 * 
 * TP: Used Cursor to implement registration hold controller
 */
@RestController
@RequestMapping("/api/holds")
@CrossOrigin(origins = "http://localhost:5173")
public class RegistrationHoldController {
    
    @Autowired
    private RegistrationHoldService holdService;
    
    /**
     * Place a registration hold on a student
     * POST /api/holds
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> placeHold(@RequestBody Map<String, Object> request) {
        try {
            Long studentId = Long.parseLong(request.get("studentId").toString());
            String holdTypeStr = request.get("holdType").toString();
            Long placedById = Long.parseLong(request.get("placedById").toString());
            String reason = (String) request.get("reason");
            
            RegistrationHold.HoldType holdType;
            try {
                holdType = RegistrationHold.HoldType.valueOf(holdTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid hold type. Must be ACADEMIC_ADVISING or FINANCIAL"
                ));
            }
            
            RegistrationHold hold = holdService.placeHold(studentId, holdType, placedById, reason);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Hold placed successfully");
            response.put("hold", Map.of(
                "holdId", hold.getHoldId(),
                "holdType", hold.getHoldType().toString(),
                "studentId", hold.getStudent().getUserId(),
                "placedAt", hold.getPlacedAt(),
                "reason", hold.getReason()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error placing hold: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Remove a registration hold
     * DELETE /api/holds/{holdId}
     */
    @DeleteMapping("/{holdId}")
    public ResponseEntity<Map<String, Object>> removeHold(
            @PathVariable Long holdId,
            @RequestParam Long removedById) {
        try {
            holdService.removeHold(holdId, removedById);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Hold removed successfully");
            response.put("deleted", true);
            response.put("holdId", holdId);
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error removing hold: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Check if student has active holds
     * GET /api/holds/check/{studentId}
     */
    @GetMapping("/check/{studentId}")
    public ResponseEntity<Map<String, Object>> checkHolds(@PathVariable Long studentId) {
        try {
            boolean hasHolds = holdService.hasActiveHolds(studentId);
            List<RegistrationHold> activeHolds = holdService.getActiveHolds(studentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("hasActiveHolds", hasHolds);
            response.put("activeHolds", activeHolds.stream().map(hold -> Map.of(
                "holdId", hold.getHoldId(),
                "holdType", hold.getHoldType().toString(),
                "placedAt", hold.getPlacedAt(),
                "reason", hold.getReason() != null ? hold.getReason() : ""
            )).toList());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error checking holds: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get all holds (active and inactive) for a student
     * GET /api/holds/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<Map<String, Object>> getAllHolds(@PathVariable Long studentId) {
        try {
            List<RegistrationHold> holds = holdService.getAllHolds(studentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("holds", holds.stream().map(hold -> Map.of(
                "holdId", hold.getHoldId(),
                "holdType", hold.getHoldType().toString(),
                "placedAt", hold.getPlacedAt(),
                "reason", hold.getReason() != null ? hold.getReason() : "",
                "placedBy", Map.of(
                    "userId", hold.getPlacedBy().getUserId(),
                    "name", hold.getPlacedBy().getFirstName() + " " + hold.getPlacedBy().getLastName()
                )
            )).toList());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error retrieving holds: " + e.getMessage()
            ));
        }
    }
}

