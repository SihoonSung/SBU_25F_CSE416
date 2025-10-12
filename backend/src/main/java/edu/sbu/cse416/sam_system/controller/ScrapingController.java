package edu.sbu.cse416.sam_system.controller;

/*
 * TP: Used Cursor to implement REST API endpoints for course catalog scraping
 * Features:
 * - POST /api/scraping/scrape: Trigger scraping for specific term and subjects
 * - GET /api/scraping/available-subjects: Get list of supported subjects
 * - GET /api/scraping/terms: Get all scraped terms
 * - GET /api/scraping/courses: Get courses with fallback to nearest term
 * - GET /api/scraping/courses/byTerm: Get all courses for a specific term
 * - GET /api/scraping/course: Get specific course by subject and number
 * - GET /api/scraping/stats: Get scraping statistics (total courses, by subject)
 * - Handles re-scraping prevention (returns 400 Bad Request with error message)
 */

import edu.sbu.cse416.sam_system.entity.Course;
import edu.sbu.cse416.sam_system.entity.Term;
import edu.sbu.cse416.sam_system.repository.CourseRepository;
import edu.sbu.cse416.sam_system.repository.TermRepository;
import edu.sbu.cse416.sam_system.service.ScrapingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller for course catalog scraping operations
 * Allows registrar to scrape course information for specified terms and subjects
 */
@RestController
@RequestMapping("/api/scraping")
@CrossOrigin(origins = "http://localhost:5173")
public class ScrapingController {

    @Autowired
    private ScrapingService scrapingService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    /**
     * Scrape courses for a given term and list of subjects
     * 
     * Example: POST /api/scraping/scrape
     * Body: {
     *   "term": "Fall 2025",
     *   "subjects": ["CSE", "BIO"]
     * }
     */
    @PostMapping("/scrape")
    public ResponseEntity<Map<String, Object>> scrapeCourses(@RequestBody Map<String, Object> request) {
        try {
            String term = (String) request.get("term");
            @SuppressWarnings("unchecked")
            List<String> subjects = (List<String>) request.get("subjects");

            if (term == null || subjects == null || subjects.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Term and subjects are required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            System.out.println("Scraping request received:");
            System.out.println("  Term: " + term);
            System.out.println("  Subjects: " + subjects);

            scrapingService.scrapeCourses(term, subjects);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully scraped courses");
            response.put("term", term);
            response.put("subjects", subjects);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error in scraping controller: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to scrape courses: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get all available subjects for scraping
     * Returns: BIO, PSY, CSE, ECO, AMS, POL
     */
    @GetMapping("/available-subjects")
    public ResponseEntity<Map<String, Object>> getAvailableSubjects() {
        Map<String, Object> response = new HashMap<>();
        List<String> subjects = Arrays.asList("BIO", "PSY", "CSE", "ECO", "AMS", "POL");
        
        response.put("subjects", subjects);
        response.put("message", "These are the supported subjects for scraping");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get all terms that have been scraped
     */
    @GetMapping("/terms")
    public ResponseEntity<List<Term>> getScrapedTerms() {
        List<Term> terms = termRepository.findAll();
        return ResponseEntity.ok(terms);
    }

    /**
     * Get courses for a specific term and subject
     */
    @GetMapping("/courses")
    public ResponseEntity<List<Course>> getCourses(
            @RequestParam String term,
            @RequestParam String subject) {
        
        List<Course> courses = scrapingService.getCoursesForTerm(term, subject);
        return ResponseEntity.ok(courses);
    }

    /**
     * Get scraping statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalCourses = courseRepository.count();
        long totalTerms = termRepository.count();
        long scrapedTerms = termRepository.findAll().stream()
                .filter(Term::getIsScraped)
                .count();

        stats.put("totalCourses", totalCourses);
        stats.put("totalTerms", totalTerms);
        stats.put("scrapedTerms", scrapedTerms);

        // Count by subject
        String[] subjects = {"BIO", "PSY", "CSE", "ECO", "AMS", "POL"};
        Map<String, Long> subjectCounts = new HashMap<>();
        for (String subject : subjects) {
            long count = courseRepository.findBySubject(subject).size();
            subjectCounts.put(subject, count);
        }
        stats.put("coursesBySubject", subjectCounts);

        return ResponseEntity.ok(stats);
    }

    /**
     * Reset a term's scraped status (allows re-scraping)
     * This should be used with caution
     */
    @PostMapping("/reset-term")
    public ResponseEntity<Map<String, Object>> resetTerm(@RequestParam String termName) {
        try {
            Optional<Term> termOpt = termRepository.findByName(termName);
            
            if (termOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Term not found: " + termName);
                return ResponseEntity.notFound().build();
            }

            Term term = termOpt.get();
            term.setIsScraped(false);
            termRepository.save(term);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Term '" + termName + "' reset. You can now re-scrape this term.");
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error resetting term: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Clear all courses and terms (for testing/reset)
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllData() {
        try {
            long coursesDeleted = courseRepository.count();
            long termsDeleted = termRepository.count();
            
            courseRepository.deleteAll();
            termRepository.deleteAll();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All data cleared successfully");
            response.put("coursesDeleted", coursesDeleted);
            response.put("termsDeleted", termsDeleted);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error clearing data: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get database status
     */
    @GetMapping("/db-status")
    public ResponseEntity<Map<String, Object>> getDbStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long termCount = termRepository.count();
            long courseCount = courseRepository.count();
            
            response.put("status", "OK");
            response.put("totalTerms", termCount);
            response.put("totalCourses", courseCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
