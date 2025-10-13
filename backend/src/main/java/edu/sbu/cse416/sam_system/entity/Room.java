package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;

/**
 * Room entity for storing room capacities
 * Used to determine class capacities based on location
 */
@Entity
@Table(name = "rooms", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"building", "room_number"})
})
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "building", nullable = false)
    private String building; // e.g., "FREY HALL"
    
    @Column(name = "room_number", nullable = false)
    private String roomNumber; // e.g., "317"
    
    @Column(name = "capacity", nullable = false)
    private Integer capacity;
    
    // Constructors
    public Room() {}
    
    public Room(String building, String roomNumber, Integer capacity) {
        this.building = building;
        this.roomNumber = roomNumber;
        this.capacity = capacity;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getBuilding() {
        return building;
    }
    
    public void setBuilding(String building) {
        this.building = building;
    }
    
    public String getRoomNumber() {
        return roomNumber;
    }
    
    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }
    
    public Integer getCapacity() {
        return capacity;
    }
    
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
    
    // Helper methods
    public String getFullLocation() {
        return building + " " + roomNumber;
    }
    
    @Override
    public String toString() {
        return "Room{" +
                "id=" + id +
                ", building='" + building + '\'' +
                ", roomNumber='" + roomNumber + '\'' +
                ", capacity=" + capacity +
                '}';
    }
}

