package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByClassEntity_ClassIdOrderByCreatedAtAsc(Long classId);

    Optional<WaitlistEntry> findFirstByClassEntityOrderByCreatedAtAsc(ClassEntity classEntity);

    boolean existsByClassEntityAndUser(ClassEntity classEntity, User user);

    long countByClassEntity(ClassEntity classEntity);

    @Modifying
    @Transactional
    void deleteByClassEntityAndUser(ClassEntity classEntity, User user);

    @Query("SELECT COALESCE(MIN(w.createdAt), CURRENT_TIMESTAMP) FROM WaitlistEntry w WHERE w.classEntity.classId = :classId")
    Optional<java.time.LocalDateTime> firstCreatedAt(@Param("classId") Long classId);
}


