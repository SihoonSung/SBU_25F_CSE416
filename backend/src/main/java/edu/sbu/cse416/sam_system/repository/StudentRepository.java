package edu.sbu.cse416.sam_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import edu.sbu.cse416.sam_system.model.Student;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    // Case-insensitive lookups for convenience
    Optional<Student> findByNetIdIgnoreCase(String netId);

    Optional<Student> findByEmailIgnoreCase(String email);

    // Legacy exact-match method (kept for compatibility if used elsewhere)
    Optional<Student> findByNetId(String netId);
}