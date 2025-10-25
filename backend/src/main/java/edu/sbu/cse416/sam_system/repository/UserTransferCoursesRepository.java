package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.UserTransferCourses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for UserTransferCourses entity.
 * Handles database operations for user's transfer courses data.
 */
@Repository
public interface UserTransferCoursesRepository extends JpaRepository<UserTransferCourses, Long> {
    
    /**
     * Find all transfer courses for a specific user
     */
    @Query("SELECT utc FROM UserTransferCourses utc WHERE utc.user.userId = :userId")
    List<UserTransferCourses> findByUserId(@Param("userId") Long userId);
    
    /**
     * Find transfer courses for a specific user and university
     */
    @Query("SELECT utc FROM UserTransferCourses utc WHERE utc.user.userId = :userId AND utc.university = :university")
    List<UserTransferCourses> findByUserIdAndUniversity(@Param("userId") Long userId, 
                                                       @Param("university") String university);
    
    /**
     * Find transfer courses with SBU equivalents (department and course_num not null)
     */
    @Query("SELECT utc FROM UserTransferCourses utc WHERE utc.user.userId = :userId AND utc.department IS NOT NULL AND utc.courseNum IS NOT NULL")
    List<UserTransferCourses> findWithSbuequivalentsByUserId(@Param("userId") Long userId);
    
    /**
     * Find transfer courses without SBU equivalents (department and course_num are null)
     */
    @Query("SELECT utc FROM UserTransferCourses utc WHERE utc.user.userId = :userId AND (utc.department IS NULL OR utc.courseNum IS NULL)")
    List<UserTransferCourses> findWithoutSbuequivalentsByUserId(@Param("userId") Long userId);
    
    /**
     * Delete all transfer courses for a specific user
     */
    @Query("DELETE FROM UserTransferCourses utc WHERE utc.user.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
