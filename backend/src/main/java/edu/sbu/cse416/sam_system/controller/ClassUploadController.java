package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import edu.sbu.cse416.sam_system.service.PdfParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REST Controller for class upload and management operations.
 * Handles PDF upload for class schedule import and class capacity management.
 * 
 * Note: Previously named "RegistrarController" - "Registrar" is the standard university term
 * for the administrative office responsible for course registration and records.
 * Renamed to "ClassUploadController" for clarity.
 * 
 * TP: Used Cursor to implement registrar functionality and file upload handling.
 */
@RestController
@RequestMapping("/api/registrar")
@CrossOrigin(origins = "http://localhost:5173")
public class ClassUploadController {
    
    @Autowired
    private PdfParserService pdfParserService;
    
    @Autowired
    private ClassRepository classRepository;
    
    /**
     * Upload PDF file with class schedule and import classes
     * 
     * @param file The PDF file
     * @param subjects Optional comma-separated list of subjects to import
     * @param semester The semester (e.g., "Spring", "Fall")
     * @param year The year (e.g., 2025)
     * @return Response with number of classes imported
     */
    @PostMapping("/upload-schedule")
    public ResponseEntity<?> uploadSchedule(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "subjects", required = false) String subjects,
            @RequestParam("semester") String semester,
            @RequestParam("year") Integer year) {
        
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }
            
            if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body("File must be a PDF");
            }
            
            // Parse subjects if provided
            Set<String> subjectSet = null;
            if (subjects != null && !subjects.trim().isEmpty()) {
                subjectSet = Set.of(subjects.split(","));
            }
            
            // Parse PDF and import classes
            int importedCount = pdfParserService.parsePdfAndImportClasses(
                file, subjectSet, semester, year
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully imported " + importedCount + " classes");
            response.put("count", importedCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error processing PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Update the capacity of a specific class
     * 
     * @param classId The class ID
     * @param capacity The new capacity
     * @return Response with updated class information
     */
    @PutMapping("/classes/{classId}/capacity")
    public ResponseEntity<?> updateClassCapacity(
            @PathVariable Long classId,
            @RequestBody Map<String, Integer> requestBody) {
        
        try {
            Integer capacity = requestBody.get("capacity");
            
            if (capacity == null || capacity < 0) {
                return ResponseEntity.badRequest().body("Invalid capacity value");
            }
            
            Optional<ClassEntity> classOptional = classRepository.findById(classId);
            
            if (classOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            ClassEntity classEntity = classOptional.get();
            classEntity.setCapacity(capacity);
            classRepository.save(classEntity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Class capacity updated successfully");
            response.put("classId", classId);
            response.put("newCapacity", capacity);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error updating capacity: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get all classes for management
     * 
     * @return List of all classes with capacity information
     */
    @GetMapping("/classes")
    public ResponseEntity<?> getAllClasses() {
        try {
            return ResponseEntity.ok(classRepository.findAll());
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error fetching classes: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Debug endpoint to preview PDF content without importing
     * 
     * @param file The PDF file
     * @return First 50 lines of extracted text for debugging
     */
    @PostMapping("/preview-pdf")
    public ResponseEntity<?> previewPdf(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty");
            }
            
            if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body("File must be a PDF");
            }
            
            // Extract text from PDF
            String text = pdfParserService.extractTextFromPdf(file);
            
            // Get first 50 lines for preview
            String[] lines = text.split("\\n");
            int previewLines = Math.min(50, lines.length);
            StringBuilder preview = new StringBuilder();
            for (int i = 0; i < previewLines; i++) {
                preview.append("Line ").append(i + 1).append(": ").append(lines[i]).append("\n");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalLines", lines.length);
            response.put("preview", preview.toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error previewing PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

