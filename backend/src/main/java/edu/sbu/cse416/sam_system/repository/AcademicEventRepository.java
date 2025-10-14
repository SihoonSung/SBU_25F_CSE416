package edu.sbu.cse416.sam_system.repository;

import edu.sbu.cse416.sam_system.entity.AcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {}