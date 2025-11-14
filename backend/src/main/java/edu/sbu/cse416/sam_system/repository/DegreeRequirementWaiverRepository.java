package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.DegreeRequirementWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for DegreeRequirementWaiver entity.
 * 
 * TP: Used Cursor to implement degree requirement waiver repository
 */
@Repository
public interface DegreeRequirementWaiverRepository extends JpaRepository<DegreeRequirementWaiver, Long> {
    
    /**
     * Find all waivers for a student
     */
    List<DegreeRequirementWaiver> findByStudent(User student);
    
    /**
     * Find all waivers for a student by student ID
     */
    @Query("SELECT dw FROM DegreeRequirementWaiver dw WHERE dw.student.userId = :studentId")
    List<DegreeRequirementWaiver> findByStudentId(@Param("studentId") Long studentId);
    
    /**
     * Find waivers for a student and subject code
     */
    @Query("SELECT dw FROM DegreeRequirementWaiver dw WHERE dw.student = :student AND dw.subjectCode = :subjectCode")
    List<DegreeRequirementWaiver> findByStudentAndSubjectCode(@Param("student") User student, @Param("subjectCode") String subjectCode);
    
    /**
     * Find waivers for a student and department
     */
    @Query("SELECT dw FROM DegreeRequirementWaiver dw WHERE dw.student = :student AND dw.department = :department")
    List<DegreeRequirementWaiver> findByStudentAndDepartment(@Param("student") User student, @Param("department") String department);
    
    /**
     * Find waivers for a student, department, and course number
     */
    @Query("SELECT dw FROM DegreeRequirementWaiver dw WHERE dw.student = :student AND dw.department = :department AND dw.courseNum = :courseNum")
    List<DegreeRequirementWaiver> findByStudentAndCourse(@Param("student") User student, 
                                                          @Param("department") String department, 
                                                          @Param("courseNum") Integer courseNum);
}

