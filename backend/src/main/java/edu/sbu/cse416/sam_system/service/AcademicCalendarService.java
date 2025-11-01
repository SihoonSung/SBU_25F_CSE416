package edu.sbu.cse416.sam_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import edu.sbu.cse416.sam_system.entity.AcademicEvent;
import edu.sbu.cse416.sam_system.repository.AcademicEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * TP: Used cursor to implement service for academic calendar management.
 */
@Service
public class AcademicCalendarService {

    private final AcademicEventRepository repo;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public AcademicCalendarService(AcademicEventRepository repo) {
        this.repo = repo;
    }

    /**
     * Supports:
     * 1) List form:
     *    - date: "2025-01-27"
     *      title: "Spring Classes Begin"
     *      type: "TERM"
     *
     * 2) Map form:
     *    academic_calendar:
     *      major_and_minor_changes_end: 2025-08-29
     *      waitlist: 2025-08-29
     *    -> title will be prettified from key, type defaults to "DEADLINE"
     */
    public Map<String, Object> importYaml(MultipartFile file) throws Exception {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            Object root = yamlMapper.readValue(is, Object.class);

            if (root instanceof List<?> listRoot) {
                // case 1: list of events
                for (int i = 0; i < listRoot.size(); i++) {
                    Object node = listRoot.get(i);
                    if (!(node instanceof Map<?, ?> m)) {
                        skipped++;
                        errors.add("Row " + (i + 1) + ": not an object");
                        continue;
                    }
                    try {
                        imported += saveFromListNode(m);
                    } catch (Exception ex) {
                        skipped++;
                        errors.add("Row " + (i + 1) + ": " + ex.getMessage());
                    }
                }
            } else if (root instanceof Map<?, ?> mapRoot) {
                // case 2: dictionary form (expect academic_calendar -> map OR list)
                Object calendar = ((Map<?, ?>) root).get("academic_calendar");
                if (calendar instanceof Map<?, ?> calMap) {
                    // Parse term information first (if present)
                    String semester = null;
                    Integer year = null;
                    Object termObj = calMap.get("term");
                    if (termObj instanceof Map<?, ?> termMap) {
                        Object semObj = termMap.get("semester");
                        Object yearObj = termMap.get("year");
                        if (semObj != null) {
                            semester = String.valueOf(semObj).trim();
                        }
                        if (yearObj != null) {
                            try {
                                year = Integer.parseInt(String.valueOf(yearObj));
                            } catch (NumberFormatException e) {
                                errors.add("Invalid year in term: " + yearObj);
                            }
                        }
                    }
                    
                    // map of key -> yyyy-MM-dd (skip "term" key)
                    for (Map.Entry<?, ?> entry : calMap.entrySet()) {
                        String rawKey = String.valueOf(entry.getKey());
                        // Skip the "term" key as it's not an event
                        if ("term".equalsIgnoreCase(rawKey)) {
                            continue;
                        }
                        
                        try {
                            String title  = prettifyKey(rawKey);
                            String dateStr = String.valueOf(entry.getValue());
                            LocalDate date = parseIsoDate(dateStr);

                            AcademicEvent ev = new AcademicEvent();
                            ev.setDate(date);
                            ev.setTitle(title);
                            ev.setType("DEADLINE");
                            // Set term information if available
                            if (semester != null) {
                                ev.setSemester(semester);
                            }
                            if (year != null) {
                                ev.setYear(year);
                            }
                            repo.save(ev);
                            imported++;
                        } catch (Exception ex) {
                            skipped++;
                            errors.add("Key " + entry.getKey() + ": " + ex.getMessage());
                        }
                    }
                } else if (calendar instanceof List<?> calList) {
                    // academic_calendar: [ {date,title,type}, ... ]
                    for (int i = 0; i < calList.size(); i++) {
                        Object node = calList.get(i);
                        if (!(node instanceof Map<?, ?> m)) {
                            skipped++;
                            errors.add("Row " + (i + 1) + ": not an object");
                            continue;
                        }
                        try {
                            imported += saveFromListNode(m);
                        } catch (Exception ex) {
                            skipped++;
                            errors.add("Row " + (i + 1) + ": " + ex.getMessage());
                        }
                    }
                } else {
                    skipped++;
                    errors.add("YAML missing 'academic_calendar' map/list");
                }
            } else {
                skipped++;
                errors.add("Unsupported YAML root type; provide a list or a map with 'academic_calendar'.");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    public List<AcademicEvent> list() {
        return repo.findAll();
    }

    public void clearAll() {
        repo.deleteAll();
    }

    // ---------- helpers ----------

    private int saveFromListNode(Map<?, ?> n) {
        String dateStr = getAsString(n, "date", true);
        String title   = getAsString(n, "title", true);
        String type    = getAsString(n, "type", false);
        if (type == null || type.isBlank()) type = "DEADLINE";

        LocalDate date = parseIsoDate(dateStr);

        AcademicEvent ev = new AcademicEvent();
        ev.setDate(date);
        ev.setTitle(title);
        ev.setType(type);
        repo.save(ev);
        return 1;
    }

    private static String getAsString(Map<?, ?> m, String key, boolean required) {
        Object v = m.get(key);
        if (v == null) {
            if (required) throw new IllegalArgumentException("Missing field '" + key + "'");
            return null;
        }
        String s = String.valueOf(v).trim();
        if (required && s.isEmpty()) {
            throw new IllegalArgumentException("Empty field '" + key + "'");
        }
        return s;
    }

    private static LocalDate parseIsoDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date (expected yyyy-MM-dd): " + s);
        }
    }

    /** Convert "major_and_minor_changes_end" -> "Major And Minor Changes End" */
    private static String prettifyKey(String key) {
        String[] parts = key.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }
}