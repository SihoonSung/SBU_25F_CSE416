package edu.sbu.cse416.sam_system.repository;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * Repository loading sample_users.yaml and providing lookups by email.
 * This keeps raw YAML structures (Map/List) to stay schema-tolerant.
 */
@Repository
public class YamlUserRepository {

    private Map<String, Object> root; // full YAML root
    private List<Map<String, Object>> registrars;
    private List<Map<String, Object>> advisors;
    private List<Map<String, Object>> instructors;
    private List<Map<String, Object>> students;

    public YamlUserRepository() {
        loadYaml();
    }

    @SuppressWarnings("unchecked")
    private void loadYaml() {
        try (InputStream in = new ClassPathResource("sample_users.yaml").getInputStream()) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(in);
            if (!(loaded instanceof Map)) {
                throw new IllegalStateException("sample_users.yaml: unexpected structure");
            }
            this.root = (Map<String, Object>) loaded;
            this.registrars  = getList(root.get("registrars"));
            this.advisors    = getList(root.get("academic_advisors"));
            this.instructors = getList(root.get("instructors"));
            this.students    = getList(root.get("students"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load sample_users.yaml from classpath", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Object o) {
        if (o instanceof List) {
            return (List<Map<String, Object>>) o;
        }
        return Collections.emptyList();
    }

    public Optional<Map<String, Object>> findStudentByEmail(String email) {
        return students.stream().filter(m -> emailEquals(m, email)).findFirst();
    }

    public Optional<Map<String, Object>> findAdvisorByEmail(String email) {
        return advisors.stream().filter(m -> emailEquals(m, email)).findFirst();
    }

    public Optional<Map<String, Object>> findRegistrarByEmail(String email) {
        return registrars.stream().filter(m -> emailEquals(m, email)).findFirst();
    }

    public Optional<Map<String, Object>> findInstructorByEmail(String email) {
        return instructors.stream().filter(m -> emailEquals(m, email)).findFirst();
    }

    private boolean emailEquals(Map<String, Object> m, String email) {
        Object v = m.get("email");
        return v != null && email != null && email.equalsIgnoreCase(String.valueOf(v));
    }

    /** Expose raw lists if needed */
    public List<Map<String, Object>> getStudents()    { return students; }
    public List<Map<String, Object>> getAdvisors()    { return advisors; }
    public List<Map<String, Object>> getRegistrars()  { return registrars; }
    public List<Map<String, Object>> getInstructors() { return instructors; }
}