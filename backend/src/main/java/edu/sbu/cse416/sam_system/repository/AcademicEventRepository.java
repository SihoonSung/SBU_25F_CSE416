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
}