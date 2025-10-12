package edu.sbu.cse416.sam_system.entity;

/*
 * TP: Used Cursor to implement Term entity for academic term management
 * Features:
 * - Tracks academic terms (e.g., "Fall 2025")
 * - Stores term dates (start/end)
 * - isScraped flag prevents re-scraping (at most once per term)
 * - One-to-many relationship with Course entities
 */

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "terms")
public class Term {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name", nullable = false, unique = true)
    private String name; // e.g., "Fall 2025"
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Column(name = "is_active")
    private Boolean isActive = false;
    
    @Column(name = "is_scraped")
    private Boolean isScraped = false;
    
    @OneToMany(mappedBy = "term", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Course> courses = new ArrayList<>();
    
    // Constructors
    public Term() {}
    
    public Term(String name) {
        this.name = name;
    }
    
    public Term(String name, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
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
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public Boolean getIsScraped() {
        return isScraped;
    }
    
    public void setIsScraped(Boolean isScraped) {
        this.isScraped = isScraped;
    }
    
    public List<Course> getCourses() {
        return courses;
    }
    
    public void setCourses(List<Course> courses) {
        this.courses = courses;
    }
    
    // Helper methods
    public void addCourse(Course course) {
        courses.add(course);
        course.setTerm(this);
    }
    
    public void removeCourse(Course course) {
        courses.remove(course);
        course.setTerm(null);
    }
    
    public int getCourseCount() {
        return courses.size();
    }
    
    @Override
    public String toString() {
        return "Term{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isActive=" + isActive +
                ", isScraped=" + isScraped +
                ", courseCount=" + getCourseCount() +
                '}';
    }
}
