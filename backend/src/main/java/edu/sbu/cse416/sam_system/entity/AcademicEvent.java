package edu.sbu.cse416.sam_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * TP: Used cursor to implement entity for academic events.
 */
@Entity
@Table(name = "academic_events")
public class AcademicEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String type; // e.g. TERM, DEADLINE

    @Column(name = "semester")
    private String semester; // Fall, Spring, Summer

    @Column(name = "year")
    private Integer year; // e.g. 2025

    // getters/setters
    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
}