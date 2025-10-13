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
    
    @Autowired
    private RoomService roomService;
    
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
    
    /**
     * Calculate and set capacity for a class based on location
     * If location is in room database → use room capacity
     * Otherwise → use default capacity (20)
     */
    public void setCapacityBasedOnLocation(ClassEntity classEntity) {
        if (classEntity.getLocation() != null) {
            int capacity = roomService.getCapacityForLocation(classEntity.getLocation());
            classEntity.setCapacity(capacity);
        } else {
            classEntity.setCapacity(20); // Default capacity
        }
    }
    
    /**
     * Update capacity for a specific class (manual edit by registrar)
     */
    public ClassEntity updateCapacity(Long classId, Integer newCapacity) {
        Optional<ClassEntity> classOpt = classRepository.findById(classId);
        if (classOpt.isPresent()) {
            ClassEntity classEntity = classOpt.get();
            classEntity.setCapacity(newCapacity);
            return classRepository.save(classEntity);
        }
        return null;
    }
    
    /**
     * Recalculate capacities for all classes based on room database
     * Useful after updating room capacities
     */
    public int recalculateAllCapacities() {
        List<ClassEntity> allClasses = classRepository.findAll();
        int count = 0;
        
        for (ClassEntity classEntity : allClasses) {
            setCapacityBasedOnLocation(classEntity);
            classRepository.save(classEntity);
            count++;
        }
        
        return count;
    }
    
    // Delete class
    public void deleteClass(Long classId) {
        classRepository.deleteById(classId);
    }
    
    // Get classes by semester and year
    public List<ClassEntity> getClassesBySemesterAndYear(String semester, Integer year) {
        return classRepository.findBySemesterAndYear(semester, year);
    }
    
    // Get all distinct semester/year combinations
    public List<Object[]> getDistinctSemesterYear() {
        return classRepository.findDistinctSemesterYear();
    }
}

