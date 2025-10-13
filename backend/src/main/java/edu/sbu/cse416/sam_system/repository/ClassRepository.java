package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for ClassEntity.
 * Provides database access methods for class/course data.
 * Includes custom queries for searching and filtering classes.
 * 
 * TP: Used Cursor to create JPA repository with custom query methods.
 */
@Repository
public interface ClassRepository extends JpaRepository<ClassEntity, Long> {
    
    // Find by department and course number
    List<ClassEntity> findByDepartmentAndCourseNum(String department, Integer courseNum);
    
    // Find by department
    List<ClassEntity> findByDepartment(String department);
    
    // Find by instructor
    List<ClassEntity> findByInstructorContainingIgnoreCase(String instructor);
    
    // Find by title
    List<ClassEntity> findByTitleContainingIgnoreCase(String title);
    
    // Search by multiple criteria
    @Query("SELECT c FROM ClassEntity c WHERE " +
           "(:department IS NULL OR c.department = :department) AND " +
           "(:courseNum IS NULL OR c.courseNum = :courseNum) AND " +
           "(:instructor IS NULL OR LOWER(c.instructor) LIKE LOWER(CONCAT('%', :instructor, '%'))) AND " +
           "(:title IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:semester IS NULL OR c.semester = :semester) AND " +
           "(:year IS NULL OR c.year = :year)")
    List<ClassEntity> searchClasses(
        @Param("department") String department,
        @Param("courseNum") Integer courseNum,
        @Param("instructor") String instructor,
        @Param("title") String title,
        @Param("semester") String semester,
        @Param("year") Integer year
    );
    
    // General search (search across class_id, code, title, and instructor) - case insensitive
    @Query("SELECT c FROM ClassEntity c WHERE " +
           "CAST(c.classId AS string) LIKE CONCAT('%', :searchTerm, '%') OR " +
           "LOWER(c.department) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(CONCAT(c.department, CAST(c.courseNum AS string))) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.instructor) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<ClassEntity> generalSearch(@Param("searchTerm") String searchTerm);
    
    // Delete all classes for a specific semester and year (bulk delete)
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteBySemesterAndYear(String semester, Integer year);
    
    // Find all classes for a specific semester and year
    List<ClassEntity> findBySemesterAndYear(String semester, Integer year);
    
    // Get all distinct semester/year combinations
    @Query("SELECT DISTINCT c.semester, c.year FROM ClassEntity c ORDER BY c.year DESC, c.semester")
    List<Object[]> findDistinctSemesterYear();
}

