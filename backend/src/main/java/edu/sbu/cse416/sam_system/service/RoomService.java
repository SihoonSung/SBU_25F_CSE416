package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.Room;
import edu.sbu.cse416.sam_system.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TP: Used cursor to implement service for managing room capacities.
 * Service for managing room capacities.
 * Loads room data from YAML file and provides capacity lookup.
 */
@Service
public class RoomService {
    
    private static final int DEFAULT_CAPACITY = 20;
    
    @Autowired
    private RoomRepository roomRepository;
    
    /**
     * Load room capacities from YAML file
     * File format: rooms1.yaml
     */
    public void loadRoomCapacitiesFromYaml(String yamlFilePath) {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(yamlFilePath);
            
            if (inputStream == null) {
                System.err.println("YAML file not found: " + yamlFilePath);
                return;
            }
            
            Map<String, Object> data = yaml.load(inputStream);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rooms = (List<Map<String, Object>>) data.get("rooms");
            
            if (rooms == null) {
                System.err.println("No 'rooms' key found in YAML file");
                return;
            }
            
            System.out.println("Loading room capacities from YAML...");
            int count = 0;
            
            for (Map<String, Object> roomData : rooms) {
                String building = (String) roomData.get("building");
                Object roomObj = roomData.get("room");
                String roomNumber = roomObj != null ? roomObj.toString() : null;
                Integer capacity = (Integer) roomData.get("capacity");
                
                if (building != null && roomNumber != null && capacity != null) {
                    // Check if room already exists
                    Optional<Room> existingRoom = roomRepository.findByBuildingAndRoomNumber(building, roomNumber);
                    
                    if (existingRoom.isPresent()) {
                        // Update existing room
                        Room room = existingRoom.get();
                        room.setCapacity(capacity);
                        roomRepository.save(room);
                    } else {
                        // Create new room
                        Room room = new Room(building, roomNumber, capacity);
                        roomRepository.save(room);
                    }
                    count++;
                }
            }
            
            System.out.println("✓ Loaded " + count + " room capacities");
            
        } catch (Exception e) {
            System.err.println("Error loading room capacities from YAML: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get capacity for a location
     * Returns room capacity if found, otherwise returns DEFAULT_CAPACITY (20)
     * 
     * @param location Full location string (e.g., "FREY HALL 317")
     * @return capacity
     */
    public int getCapacityForLocation(String location) {
        if (location == null || location.trim().isEmpty() || location.equals("TBA TBA")) {
            return DEFAULT_CAPACITY;
        }
        
        // Try to find room in database
        Optional<Room> room = roomRepository.findByLocation(location.trim());
        
        if (room.isPresent()) {
            return room.get().getCapacity();
        }
        
        // Try parsing location manually (in case format is slightly different)
        String[] parts = location.trim().split("\\s+");
        if (parts.length >= 2) {
            // Last part is room number, everything else is building
            String roomNumber = parts[parts.length - 1];
            String building = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
            
            Optional<Room> roomByParts = roomRepository.findByBuildingAndRoomNumber(building, roomNumber);
            if (roomByParts.isPresent()) {
                return roomByParts.get().getCapacity();
            }
        }
        
        // Not found in database, return default
        return DEFAULT_CAPACITY;
    }
    
    /**
     * Get all rooms
     */
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }
    
    /**
     * Add or update a room
     */
    public Room saveRoom(Room room) {
        return roomRepository.save(room);
    }
    
    /**
     * Delete all rooms (for testing)
     */
    public void deleteAllRooms() {
        roomRepository.deleteAll();
    }
}

