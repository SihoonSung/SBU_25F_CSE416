package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing registration schedule for student categories.
 * Registrars specify enrollment periods based on class standing and cumulative credits thresholds.
 * 
 * TP: Used Cursor to implement registration schedule entity.
 */
@Entity
@Table(name = "registration_schedules")
public class RegistrationSchedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;
    
    // Term information
    @Column(name = "semester", nullable = false)
    private String semester; // Fall, Spring, Summer
    
    @Column(name = "year", nullable = false)
    private Integer year; // e.g., 2025
    
    // Student category definition
    // Class standing (optional) - if null, only credits threshold applies
    @Column(name = "class_standing")
    private String classStanding; // FRESHMAN, SOPHOMORE, JUNIOR, SENIOR
    
    // Cumulative credits threshold (at least one should be set)
    @Column(name = "min_credits")
    private Integer minCredits; // Students with credits >= minCredits can register
    
    @Column(name = "max_credits")
    private Integer maxCredits; // Students with credits < maxCredits can register
    
    // Registration period
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate; // null means registration never ends
    
    // Metadata
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "description")
    private String description; // Optional description for this schedule
    
    // Constructors
    public RegistrationSchedule() {
        this.createdAt = LocalDateTime.now();
    }
    
    public RegistrationSchedule(String semester, Integer year, LocalDate startDate, LocalDate endDate) {
        this();
        this.semester = semester;
        this.year = year;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    // Getters and Setters
    public Long getScheduleId() {
        return scheduleId;
    }
    
    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }
    
    public String getSemester() {
        return semester;
    }
    
    public void setSemester(String semester) {
        this.semester = semester;
    }
    
    public Integer getYear() {
        return year;
    }
    
    public void setYear(Integer year) {
        this.year = year;
    }
    
    public String getClassStanding() {
        return classStanding;
    }
    
    public void setClassStanding(String classStanding) {
        this.classStanding = classStanding;
    }
    
    public Integer getMinCredits() {
        return minCredits;
    }
    
    public void setMinCredits(Integer minCredits) {
        this.minCredits = minCredits;
    }
    
    public Integer getMaxCredits() {
        return maxCredits;
    }
    
    public void setMaxCredits(Integer maxCredits) {
        this.maxCredits = maxCredits;
    }
    
    public LocalDate getStartDate() {
        return startDate;
    }
    
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
    
    public LocalDate getEndDate() {
        return endDate;
    }
    
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
    
    public User getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Check if a student with given cumulative credits matches this schedule's criteria
     */
    public boolean matchesStudent(Integer cumulativeCredits, String studentClassStanding) {
        // Check class standing if specified (case-insensitive and normalize)
        if (classStanding != null && !classStanding.trim().isEmpty()) {
            // Normalize both values for comparison
            String normalizedScheduleStanding = normalizeClassStanding(classStanding);
            String normalizedStudentStanding = normalizeClassStanding(studentClassStanding);
            
            if (studentClassStanding == null || 
                !normalizedScheduleStanding.equalsIgnoreCase(normalizedStudentStanding)) {
                return false;
            }
        }
        
        // Check credits threshold
        if (cumulativeCredits == null) {
            cumulativeCredits = 0;
        }
        
        // If minCredits is set, student must have at least that many credits
        if (minCredits != null && cumulativeCredits < minCredits) {
            return false;
        }
        
        // If maxCredits is set, student must have less than that many credits
        if (maxCredits != null && cumulativeCredits >= maxCredits) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if the current date is within the registration period
     * If endDate is null, registration never ends (always allowed after startDate)
     */
    public boolean isWithinPeriod(LocalDate currentDate) {
        if (!currentDate.isBefore(startDate)) {
            // If endDate is null, registration never ends
            if (endDate == null) {
                return true;
            }
            // Otherwise, check if before or equal to endDate
            return !currentDate.isAfter(endDate);
        }
        return false;
    }
    
    /**
     * Normalize class standing for comparison
     * Helper method to standardize class standing strings
     */
    private String normalizeClassStanding(String standing) {
        if (standing == null || standing.trim().isEmpty()) {
            return null;
        }
        String normalized = standing.trim().toUpperCase();
        
        if ("U1".equals(normalized) || "FRESHMEN".equals(normalized) || "FRESHMAN".equals(normalized)) {
            return "FRESHMAN";
        } else if ("U2".equals(normalized) || "SOPHOMORE".equals(normalized)) {
            return "SOPHOMORE";
        } else if ("U3".equals(normalized) || "JUNIOR".equals(normalized)) {
            return "JUNIOR";
        } else if ("U4".equals(normalized) || "SENIOR".equals(normalized)) {
            return "SENIOR";
        }
        return normalized;
    }
}

