package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.Room;
import edu.sbu.cse416.sam_system.service.ClassService;
import edu.sbu.cse416.sam_system.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TP: Used cursor to implement REST Controller for room capacity management.
 * REST Controller for room capacity management.
 * Provides endpoints for:
 * - Loading room capacities from YAML
 * - Viewing room capacities
 * - Manually editing class capacities (registrar function)
 */
@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "http://localhost:5173")
public class RoomController {

    @Autowired
    private RoomService roomService;
    
    @Autowired
    private ClassService classService;

    /**
     * Load room capacities from YAML file
     * POST /api/rooms/load-yaml
     */
    @PostMapping("/load-yaml")
    public ResponseEntity<Map<String, Object>> loadRoomCapacitiesFromYaml() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Load from default location: course_yaml/rooms1.yaml
            roomService.loadRoomCapacitiesFromYaml("course_yaml/rooms1.yaml");
            
            long totalRooms = roomService.getAllRooms().size();
            
            response.put("success", true);
            response.put("message", "Room capacities loaded successfully");
            response.put("totalRooms", totalRooms);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to load room capacities: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get all rooms
     * GET /api/rooms
     */
    @GetMapping
    public ResponseEntity<List<Room>> getAllRooms() {
        List<Room> rooms = roomService.getAllRooms();
        return ResponseEntity.ok(rooms);
    }

    /**
     * Get capacity for a specific location
     * GET /api/rooms/capacity?location=FREY HALL 317
     */
    @GetMapping("/capacity")
    public ResponseEntity<Map<String, Object>> getCapacityForLocation(@RequestParam String location) {
        Map<String, Object> response = new HashMap<>();
        
        int capacity = roomService.getCapacityForLocation(location);
        boolean inDatabase = capacity != 20 || location.equals("TBA TBA");
        
        response.put("location", location);
        response.put("capacity", capacity);
        response.put("inDatabase", inDatabase);
        response.put("message", inDatabase ? "Found in room database" : "Using default capacity (20)");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Update capacity for a specific class (manual edit by registrar)
     * PATCH /api/rooms/class/{classId}/capacity
     * Body: {"capacity": 50}
     */
    @PatchMapping("/class/{classId}/capacity")
    public ResponseEntity<Map<String, Object>> updateClassCapacity(
            @PathVariable Long classId,
            @RequestBody Map<String, Object> request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Integer newCapacity = (Integer) request.get("capacity");
            
            if (newCapacity == null || newCapacity <= 0) {
                response.put("success", false);
                response.put("message", "Invalid capacity value");
                return ResponseEntity.badRequest().body(response);
            }
            
            ClassEntity updatedClass = classService.updateCapacity(classId, newCapacity);
            
            if (updatedClass == null) {
                response.put("success", false);
                response.put("message", "Class not found");
                return ResponseEntity.notFound().build();
            }
            
            response.put("success", true);
            response.put("message", "Capacity updated successfully");
            response.put("classId", classId);
            response.put("newCapacity", newCapacity);
            response.put("location", updatedClass.getLocation());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating capacity: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Add or update a room manually
     * POST /api/rooms/add
     * Body: {"building": "FREY HALL", "roomNumber": "317", "capacity": 60}
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addRoom(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String building = (String) request.get("building");
            String roomNumber = request.get("roomNumber").toString();
            Integer capacity = (Integer) request.get("capacity");
            
            if (building == null || roomNumber == null || capacity == null) {
                response.put("success", false);
                response.put("message", "Missing required fields");
                return ResponseEntity.badRequest().body(response);
            }
            
            Room room = new Room(building, roomNumber, capacity);
            Room savedRoom = roomService.saveRoom(room);
            
            response.put("success", true);
            response.put("message", "Room added/updated successfully");
            response.put("room", savedRoom);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error adding room: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Load room capacities from server and calculate all class capacities
     * This is the main endpoint for registrars to set up capacities
     * POST /api/rooms/load-and-calculate
     */
    @PostMapping("/load-and-calculate")
    public ResponseEntity<Map<String, Object>> loadAndCalculateCapacities() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Step 1: Load room capacities from YAML
            roomService.loadRoomCapacitiesFromYaml("course_yaml/rooms1.yaml");
            long totalRooms = roomService.getAllRooms().size();
            
            // Step 2: Calculate capacities for all classes
            int updatedCount = classService.recalculateAllCapacities();
            
            response.put("success", true);
            response.put("message", "Capacity successfully changed");
            response.put("roomsLoaded", totalRooms);
            response.put("classesUpdated", updatedCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error loading capacities: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Delete all rooms (for testing)
     * DELETE /api/rooms/clear-all
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllRooms() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            roomService.deleteAllRooms();
            
            response.put("success", true);
            response.put("message", "All rooms deleted");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error deleting rooms: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}

