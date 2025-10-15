package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DegreeRequirement entity.
 * 
 * TP: Used Cursor to implement degree requirements management system
 */
@Repository
public interface DegreeRequirementRepository extends JpaRepository<DegreeRequirement, Long> {
    
    /**
     * Find active degree requirements by subject code and degree type
     */
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.subjectCode = :subjectCode " +
           "AND dr.degreeType = :degreeType AND dr.isActive = true")
    Optional<DegreeRequirement> findBySubjectCodeAndDegreeTypeAndActive(
            @Param("subjectCode") String subjectCode, 
            @Param("degreeType") DegreeType degreeType);
    
    /**
     * Find all active degree requirements for a specific subject
     */
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.subjectCode = :subjectCode " +
           "AND dr.isActive = true ORDER BY dr.effectiveTerm DESC")
    List<DegreeRequirement> findBySubjectCodeAndActive(@Param("subjectCode") String subjectCode);
    
    /**
     * Find degree requirements by effective term
     */
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.effectiveTerm = :effectiveTerm " +
           "AND dr.isActive = true ORDER BY dr.subjectCode, dr.degreeType")
    List<DegreeRequirement> findByEffectiveTermAndActive(@Param("effectiveTerm") String effectiveTerm);
    
    /**
     * Find all active degree requirements
     */
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.isActive = true " +
           "ORDER BY dr.subjectCode, dr.degreeType, dr.effectiveTerm DESC")
    List<DegreeRequirement> findAllActive();
    
    /**
     * Find restricted degree requirements
     */
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.isRestricted = true " +
           "AND dr.isActive = true ORDER BY dr.subjectCode, dr.degreeType")
    List<DegreeRequirement> findRestrictedRequirements();
    
    /**
     * Check if a degree requirement exists for a given term
     */
    @Query("SELECT COUNT(dr) > 0 FROM DegreeRequirement dr WHERE dr.subjectCode = :subjectCode " +
           "AND dr.degreeType = :degreeType AND dr.effectiveTerm = :effectiveTerm")
    boolean existsBySubjectCodeAndDegreeTypeAndEffectiveTerm(
            @Param("subjectCode") String subjectCode,
            @Param("degreeType") DegreeType degreeType,
            @Param("effectiveTerm") String effectiveTerm);
    
    /**
     * Find degree requirements by subject codes
     */
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.subjectCode IN :subjectCodes " +
           "AND dr.isActive = true ORDER BY dr.subjectCode, dr.degreeType")
    List<DegreeRequirement> findBySubjectCodeInAndActive(@Param("subjectCodes") List<String> subjectCodes);
}
