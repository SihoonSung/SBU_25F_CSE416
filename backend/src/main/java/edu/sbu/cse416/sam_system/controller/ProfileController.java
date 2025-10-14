package edu.sbu.cse416.sam_system.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import edu.sbu.cse416.sam_system.model.Student;
import edu.sbu.cse416.sam_system.repository.StudentRepository;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private StudentRepository studentRepository;

    @GetMapping("/me")
    public Student getProfile() {
        return studentRepository.findByNetId("jsyoon")
                .orElseThrow(() -> new RuntimeException("Student not found"));
    }
}