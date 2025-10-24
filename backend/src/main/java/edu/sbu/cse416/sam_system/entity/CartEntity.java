package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity class representing a course in a user's cart.
 * Maps to the 'cart' table in the database.
 * 
 * Features:
 * - Auto-generated unique ID
 * - Foreign key relationship to ClassEntity
 * - Status tracking (Added, Conflict, Enrolled, Waitlisted)
 * - User identification
 * - Timestamp tracking
 * 
 * TP: Used Cursor to implement cart database integration with foreign key relationships.
 */
@Entity
@Table(name = "cart")
public class CartEntity {
    
    // Auto-generated unique ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // User ID (for future user authentication)
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    // Foreign key to ClassEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;
    
    // Status of the course in cart (Added, Conflict, Enrolled, Waitlisted)
    @Column(name = "status", nullable = false)
    private String status;
    
    // Timestamp when added to cart
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
    
    // Constructors
    public CartEntity() {}
    
    public CartEntity(Long userId, ClassEntity classEntity, String status) {
        this.userId = userId;
        this.classEntity = classEntity;
        this.status = status;
        this.addedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public ClassEntity getClassEntity() {
        return classEntity;
    }
    
    public void setClassEntity(ClassEntity classEntity) {
        this.classEntity = classEntity;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getAddedAt() {
        return addedAt;
    }
    
    public void setAddedAt(LocalDateTime addedAt) {
        this.addedAt = addedAt;
    }
}
