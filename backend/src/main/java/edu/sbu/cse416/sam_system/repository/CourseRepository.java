package edu.sbu.cse416.sam_system.repository;

/*
 * TP: Used Cursor to implement Course repository for database operations
 * Features:
 * - Find courses by subject and term
 * - Find courses by subject and term name (for API queries)
 * - Find specific course by subject, number, and term (duplicate prevention)
 * - Count courses by term and subject (for statistics)
 * - Standard JPA repository methods (save, findAll, etc.)
 */

import edu.sbu.cse416.sam_system.entity.Course;
import edu.sbu.cse416.sam_system.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    
    // Find courses by subject and course number
    Optional<Course> findBySubjectAndCourseNumber(String subject, String courseNumber);
    
    // Find all courses by subject and course number (may return multiple results for different terms)
    List<Course> findAllBySubjectAndCourseNumber(String subject, String courseNumber);
    
    // Find specific course by subject, course number, and term (for duplicate prevention)
    Optional<Course> findBySubjectAndCourseNumberAndTerm(String subject, String courseNumber, Term term);
    
    // Find courses by subject
    List<Course> findBySubject(String subject);
    
    // Find courses by term
    List<Course> findByTerm(Term term);
    
    // Find courses by term name
    List<Course> findByTermName(String termName);
    
    // Find courses by subject and term
    List<Course> findBySubjectAndTerm(String subject, Term term);
    
    // Find courses by subject and term name
    List<Course> findBySubjectAndTermName(String subject, String termName);
    
    // Find all courses for a specific term that have been scraped
    @Query("SELECT c FROM Course c WHERE c.term = :term")
    List<Course> findScrapedCoursesForTerm(@Param("term") Term term);
    
    // Find the most recent term that has courses for a given subject
    @Query("SELECT t FROM Term t WHERE t.isScraped = true AND EXISTS (SELECT c FROM Course c WHERE c.term = t AND c.subject = :subject) ORDER BY t.startDate DESC")
    List<Term> findMostRecentScrapedTermsForSubject(@Param("subject") String subject);
    
    // Check if a course exists for a specific term
    boolean existsBySubjectAndCourseNumberAndTerm(String subject, String courseNumber, Term term);
    
    // Find courses by full course code (e.g., "CSE 416")
    @Query("SELECT c FROM Course c WHERE CONCAT(c.subject, ' ', c.courseNumber) = :courseCode")
    List<Course> findByFullCourseCode(@Param("courseCode") String courseCode);
    
    // Find courses by partial course name
    List<Course> findByCourseNameContainingIgnoreCase(String courseName);
    
    // Find courses with prerequisites
    @Query("SELECT c FROM Course c WHERE c.prerequisites IS NOT NULL AND c.prerequisites != ''")
    List<Course> findCoursesWithPrerequisites();
    
    // Count courses by subject
    long countBySubject(String subject);
    
    // Count courses by term
    long countByTerm(Term term);
    
    // Delete all courses for a specific term
    void deleteByTerm(Term term);
}
