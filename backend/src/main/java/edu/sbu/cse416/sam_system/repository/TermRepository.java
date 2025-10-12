package edu.sbu.cse416.sam_system.repository;

/*
 * TP: Used Cursor to implement Term repository for academic term management
 * Features:
 * - Find term by name (e.g., "Fall 2025")
 * - Find all scraped terms ordered by date (for fallback logic)
 * - Find terms with courses for a specific subject (fallback support)
 * - Standard JPA repository methods (save, findAll, etc.)
 */

import edu.sbu.cse416.sam_system.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TermRepository extends JpaRepository<Term, Long> {
    
    // Find term by name
    Optional<Term> findByName(String name);
    
    // Find active terms
    List<Term> findByIsActiveTrue();
    
    // Find scraped terms
    List<Term> findByIsScrapedTrue();
    
    // Find terms by date range
    List<Term> findByStartDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find terms that start after a given date
    List<Term> findByStartDateAfter(LocalDate date);
    
    // Find the most recent scraped term
    @Query("SELECT t FROM Term t WHERE t.isScraped = true ORDER BY t.startDate DESC")
    List<Term> findMostRecentScrapedTerms();
    
    // Find the nearest scraped term (for fallback when specific term not found)
    @Query("SELECT t FROM Term t WHERE t.isScraped = true ORDER BY ABS(DATEDIFF(t.startDate, :targetDate)) ASC")
    List<Term> findNearestScrapedTerm(@Param("targetDate") LocalDate targetDate);
    
    // Check if term exists by name
    boolean existsByName(String name);
    
    // Find terms that contain courses for a specific subject
    @Query("SELECT DISTINCT t FROM Term t JOIN t.courses c WHERE c.subject = :subject AND t.isScraped = true")
    List<Term> findTermsWithCoursesForSubject(@Param("subject") String subject);
    
    // Get term with most courses
    @Query("SELECT t FROM Term t WHERE t.isScraped = true ORDER BY SIZE(t.courses) DESC")
    List<Term> findTermWithMostCourses();
    
    // Find scraped terms ordered by start date descending
    @Query("SELECT t FROM Term t WHERE t.isScraped = true ORDER BY t.startDate DESC")
    List<Term> findByIsScrapedTrueOrderByStartDateDesc();
}
