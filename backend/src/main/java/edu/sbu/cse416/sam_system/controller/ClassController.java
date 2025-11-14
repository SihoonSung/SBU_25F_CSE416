package edu.sbu.cse416.sam_system.controller;

/*
 * TP: Used Cursor to implement REST API endpoints for class management
 * Features:
 * - GET /api/classes: Get all classes
 * - GET /api/classes/search: Search classes by keyword
 * - GET /api/classes/by-term: Get classes by semester and year
 * - GET /api/classes/terms: Get available semester/year combinations
 * - PUT /api/classes/{id}: Update class information (e.g., capacity)
 * - DELETE /api/classes/{id}: Delete a class
 */

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.service.ClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;

/**
 * REST API Controller for class/course operations.
 * Provides endpoints for searching, filtering, and managing classes.
 * Base URL: /api/classes
 */
@RestController
@RequestMapping("/api/classes")
@CrossOrigin(origins = "http://localhost:5173")
public class ClassController {
    
    @Autowired
    private ClassService classService;
    
    // Get all classes
    @GetMapping
    public ResponseEntity<List<ClassEntity>> getAllClasses() {
        List<ClassEntity> classes = classService.getAllClasses();
        return new ResponseEntity<>(classes, HttpStatus.OK);
    }
    
    // Get class by ID
    @GetMapping("/{id}")
    public ResponseEntity<ClassEntity> getClassById(@PathVariable Long id) {
        Optional<ClassEntity> classEntity = classService.getClassById(id);
        return classEntity.map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    // General search
    @GetMapping("/search")
    public ResponseEntity<List<ClassEntity>> searchClasses(@RequestParam(required = false) String q) {
        List<ClassEntity> classes = classService.generalSearch(q);
        return new ResponseEntity<>(classes, HttpStatus.OK);
    }
    
    // Search by department
    @GetMapping("/department/{department}")
    public ResponseEntity<List<ClassEntity>> getClassesByDepartment(@PathVariable String department) {
        List<ClassEntity> classes = classService.searchByDepartment(department);
        return new ResponseEntity<>(classes, HttpStatus.OK);
    }
    
    // Search by instructor
    @GetMapping("/instructor/{instructor}")
    public ResponseEntity<List<ClassEntity>> getClassesByInstructor(@PathVariable String instructor) {
        List<ClassEntity> classes = classService.searchByInstructor(instructor);
        return new ResponseEntity<>(classes, HttpStatus.OK);
    }
    
    // Advanced search with multiple filters
    @GetMapping("/advanced-search")
    public ResponseEntity<List<ClassEntity>> advancedSearch(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer courseNum,
            @RequestParam(required = false) String instructor,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Integer year) {
        
        List<ClassEntity> classes = classService.advancedSearch(
            department, courseNum, instructor, title, semester, year
        );
        return new ResponseEntity<>(classes, HttpStatus.OK);
    }
    
    // Create a new class
    @PostMapping
    public ResponseEntity<ClassEntity> createClass(@RequestBody ClassEntity classEntity) {
        ClassEntity savedClass = classService.saveClass(classEntity);
        return new ResponseEntity<>(savedClass, HttpStatus.CREATED);
    }
    
    // Update a class
    @PutMapping("/{id}")
    public ResponseEntity<ClassEntity> updateClass(@PathVariable Long id, @RequestBody ClassEntity classDetails) {
        Optional<ClassEntity> existingClass = classService.getClassById(id);
        
        if (existingClass.isPresent()) {
            ClassEntity classEntity = existingClass.get();
            classEntity.setDepartment(classDetails.getDepartment());
            classEntity.setCourseNum(classDetails.getCourseNum());
            classEntity.setSection(classDetails.getSection());
            classEntity.setSemester(classDetails.getSemester());
            classEntity.setYear(classDetails.getYear());
            classEntity.setMinCredits(classDetails.getMinCredits());
            classEntity.setMaxCredits(classDetails.getMaxCredits());
            classEntity.setTitle(classDetails.getTitle());
            classEntity.setInstructor(classDetails.getInstructor());
            classEntity.setSchedule(classDetails.getSchedule());
            classEntity.setLocation(classDetails.getLocation());
            
            ClassEntity updatedClass = classService.saveClass(classEntity);
            return new ResponseEntity<>(updatedClass, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    
    // Delete a class
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClass(@PathVariable Long id) {
        classService.deleteClass(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    
    // Get classes by semester and year
    @GetMapping("/by-term")
    public ResponseEntity<List<ClassEntity>> getClassesByTerm(
            @RequestParam String semester,
            @RequestParam Integer year) {
        List<ClassEntity> classes = classService.getClassesBySemesterAndYear(semester, year);
        return new ResponseEntity<>(classes, HttpStatus.OK);
    }
    
    // Get all available semester/year combinations
    @GetMapping("/terms")
    public ResponseEntity<List<Map<String, Object>>> getAvailableTerms() {
        List<Object[]> terms = classService.getDistinctSemesterYear();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Object[] term : terms) {
            Map<String, Object> termMap = new HashMap<>();
            termMap.put("semester", term[0]);
            termMap.put("year", term[1]);
            result.add(termMap);
        }
        
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}

