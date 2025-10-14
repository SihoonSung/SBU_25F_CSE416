package edu.sbu.cse416.sam_system.model;

import jakarta.persistence.*;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String netId;
    private String name;
    private String major;
    private String email;
    private String status;
    private String entryYear;

    public Student() {}

    public Long getId() { return id; }
    public String getNetId() { return netId; }
    public String getName() { return name; }
    public String getMajor() { return major; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getEntryYear() { return entryYear; }
}