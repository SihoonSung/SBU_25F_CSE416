package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * TP: Used cursor to implement repository for Room entity.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    
    // Find room by building and room number
    Optional<Room> findByBuildingAndRoomNumber(String building, String roomNumber);
    
    // Find room by full location string (e.g., "FREY HALL 317")
    @Query("SELECT r FROM Room r WHERE CONCAT(r.building, ' ', r.roomNumber) = :location")
    Optional<Room> findByLocation(@Param("location") String location);
    
    // Check if room exists
    boolean existsByBuildingAndRoomNumber(String building, String roomNumber);
    
    // Find all rooms in a building
    java.util.List<Room> findByBuilding(String building);
}

