package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.AcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * TP: Used cursor to implement repository for AcademicEvent entity.
 */
public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {
    List<AcademicEvent> findByTitleContainingIgnoreCase(String title);
    
    @Query("SELECT e FROM AcademicEvent e WHERE LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<AcademicEvent> findByTitleKeyword(@Param("keyword") String keyword);
    
    Optional<AcademicEvent> findByTitleIgnoreCase(String title);
    
    /**
     * Find event by title, semester, and year
     */
    @Query("SELECT e FROM AcademicEvent e WHERE LOWER(e.title) = LOWER(:title) AND e.semester = :semester AND e.year = :year")
    Optional<AcademicEvent> findByTitleAndSemesterAndYear(@Param("title") String title, 
                                                           @Param("semester") String semester, 
                                                           @Param("year") Integer year);
    
    /**
     * Find all distinct semester and year combinations from academic events
     */
    @Query("SELECT DISTINCT e.semester, e.year FROM AcademicEvent e WHERE e.semester IS NOT NULL AND e.year IS NOT NULL ORDER BY e.year DESC, CASE e.semester WHEN 'Spring' THEN 1 WHEN 'Fall' THEN 2 ELSE 3 END")
    List<Object[]> findDistinctSemesterAndYear();
}