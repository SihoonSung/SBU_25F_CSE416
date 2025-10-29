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
 * TP: Used cursor to implement repository for DegreeRequirement entity.
 */
@Repository
public interface DegreeRequirementRepository extends JpaRepository<DegreeRequirement, Long> {
    
    List<DegreeRequirement> findBySubjectCode(String subjectCode);
    
    List<DegreeRequirement> findBySubjectCodeAndDegreeType(String subjectCode, DegreeType degreeType);
    
    List<DegreeRequirement> findByIsRestricted(Boolean isRestricted);
    
    List<DegreeRequirement> findByIsActive(Boolean isActive);
    
    // Methods required by DegreeRequirementService
    boolean existsBySubjectCodeAndDegreeTypeAndEffectiveTerm(String subjectCode, DegreeType degreeType, String effectiveTerm);
    
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.isActive = true")
    List<DegreeRequirement> findAllActive();
    
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.subjectCode = :subjectCode AND dr.isActive = true")
    List<DegreeRequirement> findBySubjectCodeAndActive(@Param("subjectCode") String subjectCode);
    
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.effectiveTerm = :effectiveTerm AND dr.isActive = true")
    List<DegreeRequirement> findByEffectiveTermAndActive(@Param("effectiveTerm") String effectiveTerm);
    
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.subjectCode = :subjectCode AND dr.degreeType = :degreeType AND dr.isActive = true")
    Optional<DegreeRequirement> findBySubjectCodeAndDegreeTypeAndActive(@Param("subjectCode") String subjectCode, @Param("degreeType") DegreeType degreeType);
    
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.isRestricted = true AND dr.isActive = true")
    List<DegreeRequirement> findRestrictedRequirements();
    
    @Query("SELECT dr FROM DegreeRequirement dr WHERE dr.subjectCode IN :subjectCodes AND dr.isActive = true")
    List<DegreeRequirement> findBySubjectCodeInAndActive(@Param("subjectCodes") List<String> subjectCodes);
}