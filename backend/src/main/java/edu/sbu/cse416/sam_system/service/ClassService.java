package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for class/course operations.
 * Handles business logic for class searching, filtering, and management.
 * 
 * TP: Used Cursor to implement service layer methods and search logic.
 */
@Service
public class ClassService {
    
    @Autowired
    private ClassRepository classRepository;
    
    // Get all classes
    public List<ClassEntity> getAllClasses() {
        return classRepository.findAll();
    }
    
    // Get class by ID
    public Optional<ClassEntity> getClassById(Long classId) {
        return classRepository.findById(classId);
    }
    
    // Search classes by department and course number
    public List<ClassEntity> searchByDepartmentAndNumber(String department, Integer courseNum) {
        return classRepository.findByDepartmentAndCourseNum(department, courseNum);
    }
    
    // Search classes by department
    public List<ClassEntity> searchByDepartment(String department) {
        return classRepository.findByDepartment(department);
    }
    
    // Search classes by instructor
    public List<ClassEntity> searchByInstructor(String instructor) {
        return classRepository.findByInstructorContainingIgnoreCase(instructor);
    }
    
    // Search classes by title
    public List<ClassEntity> searchByTitle(String title) {
        return classRepository.findByTitleContainingIgnoreCase(title);
    }
    
    // General search (searches across all fields)
    public List<ClassEntity> generalSearch(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAllClasses();
        }
        return classRepository.generalSearch(searchTerm);
    }
    
    // Advanced search with multiple criteria
    public List<ClassEntity> advancedSearch(String department, Integer courseNum, 
                                       String instructor, String title, 
                                       String semester, Integer year) {
        return classRepository.searchClasses(department, courseNum, instructor, 
                                               title, semester, year);
    }
    
    // Create or update class
    public ClassEntity saveClass(ClassEntity classEntity) {
        return classRepository.save(classEntity);
    }
    
    // Delete class
    public void deleteClass(Long classId) {
        classRepository.deleteById(classId);
    }
}

