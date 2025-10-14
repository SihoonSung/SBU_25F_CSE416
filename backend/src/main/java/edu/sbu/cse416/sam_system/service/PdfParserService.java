package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for parsing PDF files containing class schedules from SBU's Course Offerings.
 * Extracts class information and imports them into the database.
 * 
 * TP: Used Cursor to implement PDF parsing and data extraction logic.
 */
@Service
public class PdfParserService {
    
    @Autowired
    private ClassRepository classRepository;
    
    /**
     * Parse uploaded PDF and import classes to database
     * Deletes existing classes for the same semester/year before importing new ones.
     * Uses @Transactional to ensure data consistency (rollback on failure).
     * 
     * @param file The uploaded PDF file
     * @param subjects Optional set of subjects to filter (null = import all)
     * @param semester The semester for the classes (e.g., "Spring")
     * @param year The year for the classes (e.g., 2025)
     * @return Number of classes imported
     * @throws IOException if PDF parsing fails
     */
    @org.springframework.transaction.annotation.Transactional
    public int parsePdfAndImportClasses(MultipartFile file, Set<String> subjects, 
                                        String semester, Integer year) throws IOException {
        // Delete existing classes for this semester/year (bulk delete)
        classRepository.deleteBySemesterAndYear(semester, year);
        
        // Extract text from PDF
        String pdfText = extractTextFromPdf(file);
        
        // Parse classes from text
        List<ClassEntity> classes = parseClassesFromText(pdfText, subjects, semester, year);
        
        // Save to database
        classRepository.saveAll(classes);
        
        return classes.size();
    }
    
    /**
     * Extract text content from PDF file
     */
    public String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    
    /**
     * Parse class information from PDF text
     * Handles SBU's Course Offerings PDF format
     * Format: AAS  110 G Appreciating Indian Music Credit(s): 3 SBC: ARTS
     *         85429 LEC 01 TR 12:30-01:50PM 25-AUG-2025 18-DEC-2025 FREY HALL 205 Aruna Sharma
     */
    private List<ClassEntity> parseClassesFromText(String text, Set<String> subjects, 
                                                   String semester, Integer year) {
        List<ClassEntity> classes = new ArrayList<>();
        
        // Split by lines
        String[] lines = text.split("\\n");
        
        // Pattern for course header line
        // Example: "AAS  110 G Appreciating Indian Music Credit(s): 3 SBC: ARTS"
        // Also handles ranges like "Credit(s): 1 to 6"
        Pattern courseHeaderPattern = Pattern.compile(
            "^([A-Z]{3})\\s+(\\d{3})\\s+([A-Z]?)\\s+(.+?)\\s+Credit\\(s\\):\\s+(\\d+(?:\\s+to\\s+\\d+)?)(?:\\s+SBC:\\s+(.+?))?$"
        );
        
        // Pattern for class detail line with class ID
        // Example: "89556 LAB L01 MW 08:00-09:20AM 25-AUG-2025 18-DEC-2025 COMPUTER SCI 2311 Jalaa Hoblos"
        Pattern classDetailWithIdPattern = Pattern.compile(
            "^\\s*(\\d{5})\\s+([A-Z]{3})\\s+([A-Z]?\\d{2})\\s+([A-Za-z]+)\\s+([\\d:APM-]+|TBA|ONLINE)\\s+(.+)$"
        );
        
        // Pattern for class detail line WITHOUT class ID (like LEC lines)
        // Example: "LEC 01 TR 02:00-03:20PM 25-AUG-2025 18-DEC-2025 HARRIMAN HLL 137 Jalaa Hoblos"
        Pattern classDetailNoIdPattern = Pattern.compile(
            "^\\s*([A-Z]{3})\\s+([A-Z]?\\d{2})\\s+([A-Za-z]+)\\s+([\\d:APM-]+|TBA|ONLINE)\\s+(.+)$"
        );
        
        String currentDept = null;
        Integer currentCourseNum = null;
        String currentTitle = null;
        Integer currentMinCredits = null;
        Integer currentMaxCredits = null;
        String currentSbc = null;
        String currentPrerequisites = "None";
        String currentCorequisites = "None";
        String currentAntirequisites = "None";
        
        // Store LEC information for courses with LAB sections
        String lecSchedule = null;
        String lecLocation = null;
        String lecInstructor = null;
        
        // Track processed class IDs to avoid duplicates
        java.util.Set<Long> processedClassIds = new java.util.HashSet<>();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            if (line.isEmpty() || line.startsWith("_") || line.startsWith("Note:")) {
                continue;
            }
            
            // Check if this is a course header line
            Matcher courseHeaderMatcher = courseHeaderPattern.matcher(line);
            if (courseHeaderMatcher.find()) {
                currentDept = courseHeaderMatcher.group(1).trim();
                currentCourseNum = Integer.parseInt(courseHeaderMatcher.group(2).trim());
                currentTitle = courseHeaderMatcher.group(4).trim();
                
                // Parse credits - handle ranges like "1 to 6"
                String creditsStr = courseHeaderMatcher.group(5).trim();
                if (creditsStr.contains("to")) {
                    // Extract both min and max from range (e.g., "1 to 6" -> min=1, max=6)
                    String[] parts = creditsStr.split("\\s+to\\s+");
                    currentMinCredits = Integer.parseInt(parts[0].trim());
                    currentMaxCredits = Integer.parseInt(parts[1].trim());
                } else {
                    // Fixed credit (e.g., "3" -> min=3, max=3)
                    currentMinCredits = Integer.parseInt(creditsStr);
                    currentMaxCredits = Integer.parseInt(creditsStr);
                }
                
                // Extract SBC if present (group 6)
                currentSbc = courseHeaderMatcher.groupCount() >= 6 && courseHeaderMatcher.group(6) != null 
                    ? courseHeaderMatcher.group(6).trim() : null;
                currentPrerequisites = "None"; // Reset for new course
                currentCorequisites = "None";
                currentAntirequisites = "None";
                lecSchedule = null; // Reset LEC info
                lecLocation = null;
                lecInstructor = null;
                
                // Filter by subjects if specified
                if (subjects != null && !subjects.isEmpty() && !subjects.contains(currentDept)) {
                    currentDept = null;
                    currentCourseNum = null;
                    currentTitle = null;
                    currentMinCredits = null;
                    currentMaxCredits = null;
                    currentSbc = null;
                    currentPrerequisites = "None";
                    currentCorequisites = "None";
                    currentAntirequisites = "None";
                }
                continue;
            }
            
            // Check if this is a prerequisite/corequisite/antirequisite line
            // Handles multi-line requisites (e.g., text wrapped across multiple lines)
            // Support both singular and plural forms (Prerequisite/Prerequisites)
            if (line.startsWith("Prerequisite") || line.startsWith("Advisory Prerequisite") || 
                line.startsWith("Pre- or Corequisite") ||
                line.startsWith("Corequisite") || line.startsWith("Advisory Corequisite") ||
                line.startsWith("Antirequisite") || line.contains("Anti-requisite")) {
                
                if (currentDept != null) {
                    // Collect the full text including continuation on next lines
                    StringBuilder fullText = new StringBuilder(line);
                    
                    // Check next lines for continuation (lines that don't start with a new section/course)
                    while (i + 1 < lines.length) {
                        String nextLine = lines[i + 1].trim();
                        
                        // Stop if we hit a new course header (DEPT NUM Title)
                        if (nextLine.matches("^[A-Z]{3}\\s+\\d{3}.*") || 
                            nextLine.matches("^[A-Z]{3}\\s+\\d{2}\\s.*")) {
                            break;
                        }
                        
                        // Stop if we hit a class detail line (with ID: 5-digit followed by section type)
                        if (nextLine.matches("^\\d{5}\\s+(LEC|LAB|REC|TUT|SEM|STU|IND|PRA|CLN|FLD|INT|THE|WKS).*")) {
                            break;
                        }
                        
                        // Stop if we hit a class detail line (without ID: section type at start)
                        // e.g., "REC R02 M 08:25-09:20AM ..."
                        if (nextLine.matches("^(LEC|LAB|REC|TUT|SEM|STU|IND|PRA|CLN|FLD|INT|THE|WKS)\\s+[A-Z]?\\d{2}\\s+.*")) {
                            break;
                        }
                        
                        // Stop if we hit table headers or separators
                        if (nextLine.startsWith("DEPT CRS") || 
                            nextLine.startsWith("DEPT") && nextLine.contains("CRS") ||
                            nextLine.contains("Cls Nbr") ||
                            nextLine.contains("Cls_Nbr") ||
                            nextLine.startsWith("_") ||
                            nextLine.matches("^_+$") ||
                            nextLine.startsWith("Undergraduate Courses") ||
                            nextLine.startsWith("Graduate Courses")) {
                            break;
                        }
                        
                        // Stop if we hit a new requisite line
                        if (nextLine.startsWith("Prerequisite") ||
                            nextLine.startsWith("Pre- or Corequisite") ||
                            nextLine.startsWith("Corequisite") ||
                            nextLine.startsWith("Antirequisite") ||
                            nextLine.startsWith("Advisory") ||
                            nextLine.startsWith("Note:")) {
                            break;
                        }
                        
                        // If line is not empty and doesn't match stop conditions, it's a continuation
                        if (!nextLine.isEmpty()) {
                            fullText.append(" ").append(nextLine);
                            i++; // Skip this line in the main loop
                        } else {
                            // Empty line might indicate end of continuation
                            break;
                        }
                    }
                    
                    String fullLine = fullText.toString();
                    
                    // Simple approach: split by semicolon and process each part
                    String[] parts = fullLine.split(";");
                    StringBuilder prereqBuilder = new StringBuilder();
                    StringBuilder coreqBuilder = new StringBuilder();
                    StringBuilder antireqBuilder = new StringBuilder();
                    
                    for (String part : parts) {
                        part = part.trim();
                        
                        // Handle "Pre- or Corequisite: ..."
                        if (part.startsWith("Pre- or Corequisite:")) {
                            String content = part.substring("Pre- or Corequisite:".length()).trim();
                            
                            // Add to prerequisites
                            if (prereqBuilder.length() > 0) prereqBuilder.append(", or ");
                            prereqBuilder.append(content);
                            
                            // Add to corequisites
                            coreqBuilder.append(content);
                        }
                        // Handle regular "Prerequisite: ..." (may contain "or Corequisite:")
                        else if (part.startsWith("Prerequisite:") || part.startsWith("Prerequisites:")) {
                            String content = part.startsWith("Prerequisite:") 
                                ? part.substring("Prerequisite:".length()).trim()
                                : part.substring("Prerequisites:".length()).trim();
                            
                            // Just add the whole content to prerequisites (including "or Corequisite: X")
                            if (prereqBuilder.length() > 0) prereqBuilder.append("; ");
                            prereqBuilder.append(content);
                        }
                        // Handle regular "Corequisite: ..."
                        else if (part.startsWith("Corequisite:") || part.startsWith("Corequisites:")) {
                            String content = part.startsWith("Corequisite:") 
                                ? part.substring("Corequisite:".length()).trim()
                                : part.substring("Corequisites:".length()).trim();
                            
                            if (coreqBuilder.length() > 0) coreqBuilder.append("; ");
                            coreqBuilder.append(content);
                        }
                        // Handle "Antirequisite: ..."
                        else if (part.startsWith("Antirequisite:") || part.startsWith("Anti-requisite:")) {
                            String content = part.startsWith("Antirequisite:") 
                                ? part.substring("Antirequisite:".length()).trim()
                                : part.substring("Anti-requisite:".length()).trim();
                            
                            if (antireqBuilder.length() > 0) antireqBuilder.append("; ");
                            antireqBuilder.append(content);
                        }
                    }
                    
                    // Update current values
                    if (prereqBuilder.length() > 0) {
                        currentPrerequisites = prereqBuilder.toString();
                    }
                    if (coreqBuilder.length() > 0) {
                        currentCorequisites = coreqBuilder.toString();
                    }
                    if (antireqBuilder.length() > 0) {
                        currentAntirequisites = antireqBuilder.toString();
                    }
                }
                continue;
            }
            
            // Check if this is a LEC/REC line (without ID)
            // LEC/REC sections without IDs are informational only, but store for reference
            Matcher classDetailNoIdMatcher = classDetailNoIdPattern.matcher(line);
            if (classDetailNoIdMatcher.find() && currentDept != null) {
                String type = classDetailNoIdMatcher.group(1).trim();
                if (type.equals("LEC") || type.equals("REC")) {
                    // Store LEC/REC information for later use with LAB sections
                    String days = classDetailNoIdMatcher.group(3).trim();
                    String timeSchedule = classDetailNoIdMatcher.group(4).trim();
                    String restOfLine = classDetailNoIdMatcher.group(5).trim();
                    
                    // Parse location and instructor from restOfLine
                    String[] parts = restOfLine.split("\\s+");
                    StringBuilder locBuilder = new StringBuilder();
                    StringBuilder instrBuilder = new StringBuilder();
                    boolean foundRoom = false;
                    int startIndex = 0;
                    
                    // Skip dates if present
                    if (parts.length > 2 && parts[0].contains("-") && parts[1].contains("-")) {
                        startIndex = 2;
                    }
                    
                    for (int j = startIndex; j < parts.length; j++) {
                        String part = parts[j];
                        if (!foundRoom && part.matches("\\d+[A-Z]?")) {
                            locBuilder.append(" ").append(part);
                            foundRoom = true;
                        } else if (!foundRoom && part.matches("[A-Z]+")) {
                            if (locBuilder.length() > 0) locBuilder.append(" ");
                            locBuilder.append(part);
                        } else if (foundRoom || !part.matches("[A-Z]+")) {
                            if (instrBuilder.length() > 0) instrBuilder.append(" ");
                            instrBuilder.append(part);
                        }
                    }
                    
                    lecSchedule = days + " " + timeSchedule;
                    lecLocation = locBuilder.length() > 0 ? locBuilder.toString().trim() : "TBA";
                    lecInstructor = instrBuilder.length() > 0 ? instrBuilder.toString().trim() : "TBA";
                }
                continue;
            }
            
            // Check if this is a class detail line (with ID)
            // These are actual sections that can be registered (LAB, REC, etc.)
            Matcher classDetailWithIdMatcher = classDetailWithIdPattern.matcher(line);
            
            if (classDetailWithIdMatcher.find() && currentDept != null) {
                try {
                    // Extract class information
                    Long classId = Long.parseLong(classDetailWithIdMatcher.group(1).trim());
                    
                    // Skip if this class ID has already been processed (avoid duplicates)
                    if (processedClassIds.contains(classId)) {
                        continue;
                    }
                    processedClassIds.add(classId);
                    
                    String sectionType = classDetailWithIdMatcher.group(2).trim(); // LEC, LAB, REC, etc.
                    String section = classDetailWithIdMatcher.group(3).trim();
                    String days = classDetailWithIdMatcher.group(4).trim();
                    String timeSchedule = classDetailWithIdMatcher.group(5).trim();
                    String restOfLine = classDetailWithIdMatcher.group(6).trim();
                    
                    // Parse the rest of the line to extract location and instructor
                    // Format: "25-AUG-2025 18-DEC-2025 PHYSICS A117 TBA"
                    // Expected: Dates + Building (uppercase) + Room (alphanumeric) + Instructor (rest)
                    String location = "TBA";
                    String instructor = "TBA";
                    
                    // Split by whitespace
                    String[] parts = restOfLine.split("\\s+");
                    
                    // Skip dates (first 2 parts if they look like dates)
                    int startIndex = 0;
                    if (parts.length > 2 && parts[0].contains("-") && parts[1].contains("-")) {
                        startIndex = 2; // Skip date range
                    }
                    
                    // Parse building, room, instructor
                    if (parts.length > startIndex) {
                        StringBuilder buildingBuilder = new StringBuilder();
                        String room = null;
                        StringBuilder instructorBuilder = new StringBuilder();
                        
                        int idx = startIndex;
                        
                        // Collect building name (all uppercase words)
                        while (idx < parts.length && parts[idx].matches("[A-Z]+")) {
                            if (buildingBuilder.length() > 0) buildingBuilder.append(" ");
                            buildingBuilder.append(parts[idx]);
                            idx++;
                        }
                        
                        // Next should be room number (alphanumeric)
                        if (idx < parts.length && parts[idx].matches("[A-Z]?\\d+[A-Z]?")) {
                            room = parts[idx];
                            idx++;
                        }
                        
                        // Everything else is the instructor
                        while (idx < parts.length) {
                            if (instructorBuilder.length() > 0) instructorBuilder.append(" ");
                            instructorBuilder.append(parts[idx]);
                            idx++;
                        }
                        
                        // Build location
                        if (buildingBuilder.length() > 0 && room != null) {
                            location = buildingBuilder.toString() + " " + room;
                        } else if (buildingBuilder.length() > 0) {
                            location = buildingBuilder.toString();
                        }
                        
                        // Set instructor
                        if (instructorBuilder.length() > 0) {
                            instructor = instructorBuilder.toString();
                        }
                    }
                    
                    String schedule = days + " " + timeSchedule;
                    
                    ClassEntity classEntity = new ClassEntity();
                    classEntity.setClassId(classId); // Set class ID from PDF
                    classEntity.setDepartment(currentDept);
                    classEntity.setCourseNum(currentCourseNum);
                    classEntity.setSection(section);
                    classEntity.setSectionType(sectionType); // Set section type (LEC/LAB/REC)
                    classEntity.setTitle(currentTitle);
                    classEntity.setInstructor(instructor.isEmpty() ? "TBA" : instructor);
                    classEntity.setSchedule(schedule);
                    classEntity.setLocation(location.isEmpty() ? "TBA" : location);
                    
                    // If this is LAB or REC and we have LEC info, add it
                    if ((sectionType.equals("LAB") || sectionType.equals("REC")) && lecSchedule != null) {
                        classEntity.setLecSchedule(lecSchedule);
                        classEntity.setLecLocation(lecLocation);
                    }
                    
                    classEntity.setSemester(semester);
                    classEntity.setYear(year);
                    classEntity.setMinCredits(currentMinCredits);
                    classEntity.setMaxCredits(currentMaxCredits);
                    classEntity.setSbc(currentSbc); // Set SBC
                    classEntity.setPrerequisites(currentPrerequisites);
                    classEntity.setCorequisites(currentCorequisites);
                    classEntity.setAntirequisites(currentAntirequisites);
                    classEntity.setCapacity(100); // Default capacity
                    
                    classes.add(classEntity);
                } catch (Exception e) {
                    System.err.println("Error parsing class detail: " + line);
                    e.printStackTrace();
                }
            }
        }
        
        return classes;
    }
    
    /**
     * Helper method to find the start position of the next requisite keyword in a string.
     * Used to extract the text between two requisite keywords.
     * 
     * @param text The full text to search
     * @param startPos The position to start searching from
     * @return The position of the next requisite keyword, or end of string if not found
     */
    private int findNextRequisiteStart(String text, int startPos) {
        int minPos = text.length();
        
        // Check for all possible requisite keywords
        String[] keywords = {
            "Prerequisite:", "Prerequisites:",
            "Advisory Prerequisite:", "Advisory Prerequisites:",
            "Pre- or Corequisite:", "Pre- or Corequisites:",
            "Corequisite:", "Corequisites:",
            "Advisory Corequisite:", "Advisory Corequisites:",
            "Antirequisite:", "Antirequisites:",
            "Anti-requisite:", "Anti-requisites:"
        };
        
        for (String keyword : keywords) {
            int pos = text.indexOf(keyword, startPos);
            if (pos != -1 && pos < minPos) {
                minPos = pos;
            }
        }
        
        return minPos;
    }
    
}

