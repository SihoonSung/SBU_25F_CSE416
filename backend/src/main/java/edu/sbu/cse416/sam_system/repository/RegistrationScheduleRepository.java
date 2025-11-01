package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.RegistrationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository interface for RegistrationSchedule entity.
 * 
 * TP: Used Cursor to implement JPA repository interface.
 */
@Repository
public interface RegistrationScheduleRepository extends JpaRepository<RegistrationSchedule, Long> {
    
    /**
     * Find all schedules for a specific term
     */
    @Query("SELECT rs FROM RegistrationSchedule rs WHERE rs.semester = :semester AND rs.year = :year ORDER BY rs.startDate ASC")
    List<RegistrationSchedule> findBySemesterAndYear(@Param("semester") String semester, @Param("year") Integer year);
    
    /**
     * Find schedules that are active on a given date
     * If endDate is null, registration never ends (only check startDate)
     */
    @Query("SELECT rs FROM RegistrationSchedule rs WHERE :date >= rs.startDate " +
           "AND (rs.endDate IS NULL OR :date <= rs.endDate)")
    List<RegistrationSchedule> findActiveSchedules(@Param("date") LocalDate date);
    
    /**
     * Find schedules for a specific term that are active on a given date
     * If endDate is null, registration never ends (only check startDate)
     */
    @Query("SELECT rs FROM RegistrationSchedule rs WHERE rs.semester = :semester AND rs.year = :year " +
           "AND :date >= rs.startDate AND (rs.endDate IS NULL OR :date <= rs.endDate)")
    List<RegistrationSchedule> findActiveSchedulesForTerm(@Param("semester") String semester, 
                                                          @Param("year") Integer year,
                                                          @Param("date") LocalDate date);
    
}

