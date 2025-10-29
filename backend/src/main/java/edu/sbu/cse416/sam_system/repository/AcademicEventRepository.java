package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.AcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * TP: Used cursor to implement repository for AcademicEvent entity.
 */
public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {}