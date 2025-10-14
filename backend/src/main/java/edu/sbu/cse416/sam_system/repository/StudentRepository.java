package edu.sbu.cse416.sam_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import edu.sbu.cse416.sam_system.model.Student;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByNetId(String netId);
}