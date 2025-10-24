package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Repository interface for CartEntity.
 * Provides database operations for cart functionality.
 * 
 * Features:
 * - Find cart items by user ID
 * - Find cart items by user ID and class ID
 * - Delete cart items
 * - Update status for conflict detection
 * 
 * TP: Used Cursor to implement JPA repository with custom queries.
 */
@Repository
public interface CartRepository extends JpaRepository<CartEntity, Long> {
    
    /**
     * Find all cart items for a specific user
     * @param userId User ID
     * @return List of cart items
     */
    @Query("SELECT c FROM CartEntity c WHERE c.userId = :userId ORDER BY c.addedAt DESC")
    List<CartEntity> findByUserId(@Param("userId") Long userId);
    
    /**
     * Find cart item by user ID and class ID
     * @param userId User ID
     * @param classId Class ID
     * @return Cart item if exists, null otherwise
     */
    @Query("SELECT c FROM CartEntity c WHERE c.userId = :userId AND c.classEntity.id = :classId")
    CartEntity findByUserIdAndClassId(@Param("userId") Long userId, @Param("classId") Long classId);
    
    /**
     * Check if a class is already in user's cart
     * @param userId User ID
     * @param classId Class ID
     * @return true if exists, false otherwise
     */
    @Query("SELECT COUNT(c) > 0 FROM CartEntity c WHERE c.userId = :userId AND c.classEntity.id = :classId")
    boolean existsByUserIdAndClassId(@Param("userId") Long userId, @Param("classId") Long classId);
    
    /**
     * Delete cart item by user ID and class ID
     * @param userId User ID
     * @param classId Class ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CartEntity c WHERE c.userId = :userId AND c.classEntity.id = :classId")
    void deleteByUserIdAndClassId(@Param("userId") Long userId, @Param("classId") Long classId);
    
    /**
     * Delete all cart items for a user
     * @param userId User ID
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM CartEntity c WHERE c.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
    
    /**
     * Update status for specific cart item
     * @param userId User ID
     * @param classId Class ID
     * @param status New status
     */
    @Modifying
    @Transactional
    @Query("UPDATE CartEntity c SET c.status = :status WHERE c.userId = :userId AND c.classEntity.id = :classId")
    void updateStatusByUserIdAndClassId(@Param("userId") Long userId, @Param("classId") Long classId, @Param("status") String status);
}
