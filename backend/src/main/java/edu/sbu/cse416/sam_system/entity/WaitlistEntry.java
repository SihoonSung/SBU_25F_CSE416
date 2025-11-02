package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * TP: Used Cursor to implement waitlist entry entity.
 * Entity class representing a student's entry on a class waitlist.
 * Maps to the 'waitlist' table in the database.
 * 
 * Features:
 * - Unique constraint on class_id and user_id combination
 * - Status tracking (WAITING, ENROLLED, CANCELLED)
 * - Timestamp tracking for queue position
 */
@Entity
@Table(name = "waitlist", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"class_id", "user_id"})
})
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waitlist_id")
    private Long waitlistId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassEntity classEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "status", nullable = false)
    private String status; // WAITING, ENROLLED, CANCELLED

    public WaitlistEntry() {
    }

    public WaitlistEntry(ClassEntity classEntity, User user) {
        this.classEntity = classEntity;
        this.user = user;
        this.createdAt = LocalDateTime.now();
        this.status = "WAITING";
    }

    public Long getWaitlistId() {
        return waitlistId;
    }

    public void setWaitlistId(Long waitlistId) {
        this.waitlistId = waitlistId;
    }

    public ClassEntity getClassEntity() {
        return classEntity;
    }

    public void setClassEntity(ClassEntity classEntity) {
        this.classEntity = classEntity;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}


