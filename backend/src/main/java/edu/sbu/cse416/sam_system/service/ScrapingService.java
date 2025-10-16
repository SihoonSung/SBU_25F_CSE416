package edu.sbu.cse416.sam_system.service;

/**
 * Web scraping service for SBU course catalog.
 * Implements comprehensive course data extraction with advanced parsing capabilities.
 * 
 * Features:
 * - Two-step scraping: course list page → individual course detail pages
 * - Extracts for ALL subjects: description, credits (including ranges), SBCs
 * - Extracts for FULLY SUPPORTED subjects (BIO, PSY, CSE, ECO, AMS, POL): prerequisites, corequisites, antirequisites, advisory prereqs
 * - Prevents duplicate entries (updates existing courses if found)
 * - Allows scraping additional subjects for the same term (extension), but prevents re-scraping the same subject
 * - Handles placement exam simplification with warnings
 * - Enhanced fallback mechanism: uses nearest available term's data if requested term not scraped
 * - No data deletion on re-scraping: maintains data integrity by preventing subject-level re-scraping
 * - Credit range support: handles "0-6 credits" format with separate min/max fields
 * 
 * Note: Complex web scraping logic and HTML parsing were implemented with assistance from Cursor AI and ChatGPT
 * for proper Jsoup integration and robust data extraction patterns.
 */

import edu.sbu.cse416.sam_system.entity.Course;
import edu.sbu.cse416.sam_system.entity.Term;
import edu.sbu.cse416.sam_system.repository.CourseRepository;
import edu.sbu.cse416.sam_system.repository.TermRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to represent credit range information
 */
class CreditRange {
    final Integer minCredits;
    final Integer maxCredits;
    
    CreditRange(Integer minCredits, Integer maxCredits) {
        this.minCredits = minCredits;
        this.maxCredits = maxCredits;
    }
}

@Service
public class ScrapingService {

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private TermRepository termRepository;

    private static final String CATALOG_BASE_URL = "https://catalog.stonybrook.edu/content.php?catoid=7&navoid=225";
    private static final String COURSE_DETAIL_BASE_URL = "https://catalog.stonybrook.edu/preview_course_nopop.php";
    
    // Supported subjects with full information extraction
    private static final List<String> FULLY_SUPPORTED_SUBJECTS = Arrays.asList("BIO", "PSY", "CSE", "ECO", "AMS", "POL");

    /**
     * Scrapes courses for specified subjects and term from Stony Brook course catalog
     * Allows scraping additional subjects for the same term (extension), but prevents re-scraping the same subject
     * Returns a map with scraping results for each subject
     */
    @Transactional
    public Map<String, Object> scrapeCourses(String termName, List<String> subjects) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COURSE SCRAPING START");
        System.out.println("Term: " + termName);
        System.out.println("Subjects: " + subjects);
        System.out.println("=".repeat(80));

        // Get or create term
        Term term = getOrCreateTerm(termName);

        // Initialize result tracking
        Map<String, Object> results = new HashMap<>();
        List<String> scrapedSubjects = new ArrayList<>();
        List<String> skippedSubjects = new ArrayList<>();
        List<String> failedSubjects = new ArrayList<>();
        int totalScraped = 0;

        for (String subject : subjects) {
            try {
                System.out.println("\n" + "-".repeat(80));
                System.out.println("Scraping subject: " + subject);
                System.out.println("-".repeat(80));

                // Check if this subject has already been scraped for this term
                List<Course> existingCourses = courseRepository.findBySubjectAndTerm(subject, term);
                if (!existingCourses.isEmpty()) {
                    System.out.println("WARNING: Subject '" + subject + "' has already been scraped for term '" + termName + "' (" + existingCourses.size() + " courses found).");
                    System.out.println("   Skipping to prevent duplicate scraping of the same subject and term combination.");
                    skippedSubjects.add(subject);
                    continue;
                }

                // Check if subject is supported
                if (!FULLY_SUPPORTED_SUBJECTS.contains(subject)) {
                    System.out.println("WARNING: Subject '" + subject + "' is not in the fully supported list (BIO, PSY, CSE, ECO, AMS, POL).");
                    System.out.println("   Requisite information will be marked as 'unknown'.");
                }

                int coursesScraped = scrapeSubject(subject, term);
                totalScraped += coursesScraped;

                System.out.println("✓ Scraped " + coursesScraped + " courses for " + subject);
                scrapedSubjects.add(subject);

            } catch (Exception e) {
                System.err.println("✗ Error scraping " + subject + ": " + e.getMessage());
                e.printStackTrace();
                failedSubjects.add(subject);
            }
        }

        // Mark term as scraped if we scraped any new courses
        if (totalScraped > 0) {
            term.setIsScraped(true);
            termRepository.save(term);
        }

        // Prepare results
        results.put("totalScraped", totalScraped);
        results.put("scrapedSubjects", scrapedSubjects);
        results.put("skippedSubjects", skippedSubjects);
        results.put("failedSubjects", failedSubjects);
        results.put("term", termName);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("COURSE SCRAPING COMPLETED");
        System.out.println("Total courses scraped: " + totalScraped);
        System.out.println("Subjects scraped: " + scrapedSubjects);
        System.out.println("Subjects skipped (already scraped): " + skippedSubjects);
        System.out.println("Subjects failed: " + failedSubjects);
        System.out.println("=".repeat(80) + "\n");

        return results;
    }

    /**
     * Scrapes all courses for a single subject
     */
    private int scrapeSubject(String subject, Term term) throws IOException {
        int count = 0;

        // Step 1: Get the course list page
        Document listDoc = Jsoup.connect(CATALOG_BASE_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .data("filter[27]", subject)
                        .data("filter[32]", "1")
                .timeout(30000)
                .ignoreHttpErrors(true)
                        .get();

        // Step 2: Find all course links with onclick="showCourse(...)"
        Elements courseTds = listDoc.select("td.width");
        
        System.out.println("Found " + courseTds.size() + " potential course entries");

        for (Element td : courseTds) {
            Element anchor = td.selectFirst("a");
            if (anchor == null) continue;

            String href = anchor.attr("href");
            String anchorText = anchor.text();

            // Parse course code from anchor text: "CSE 101 - Computer Science Principles"
            Pattern pattern = Pattern.compile("^([A-Z]{3})\\s+(\\d{3})\\s*[–-]\\s*(.+)$");
            Matcher matcher = pattern.matcher(anchorText);

            if (!matcher.find()) continue;

            String courseSubject = matcher.group(1);
            String courseNumber = matcher.group(2);
            String courseName = matcher.group(3).trim();

            // Filter by subject
            if (!courseSubject.equals(subject)) continue;

            try {
                // Step 3: Fetch course detail page
                String detailUrl = "https://catalog.stonybrook.edu/" + href;
                Course course = scrapeCourseDetail(detailUrl, courseSubject, courseNumber, courseName, term);

                if (course != null) {
                    // Check if course already exists for this term
                    boolean exists = courseRepository.existsBySubjectAndCourseNumberAndTerm(
                        course.getSubject(), course.getCourseNumber(), term);
                    
                    if (exists) {
                        // Find and update existing course
                        List<Course> existingCourses = courseRepository.findBySubjectAndTerm(
                            course.getSubject(), term);
                        
                        Course existing = null;
                        for (Course c : existingCourses) {
                            if (c.getCourseNumber().equals(course.getCourseNumber())) {
                                existing = c;
                                break;
                            }
                        }
                        
                        if (existing != null) {
                            existing.setCourseName(course.getCourseName());
                            existing.setDescription(course.getDescription());
                            existing.setCredits(course.getCredits());
                            existing.setMaxCredits(course.getMaxCredits());
                            existing.setPrerequisites(course.getPrerequisites());
                            existing.setCorequisites(course.getCorequisites());
                            existing.setAntirequisites(course.getAntirequisites());
                            existing.setAdvisoryPrerequisites(course.getAdvisoryPrerequisites());
                            existing.setSbcsSatisfied(course.getSbcsSatisfied());
                            existing.setWarning(course.getWarning());
                            courseRepository.save(existing);
                            System.out.println(String.format("\n[%d] UPDATED: %s %s - %s", 
                                count + 1, course.getSubject(), course.getCourseNumber(), course.getCourseName()));
                        }
                    } else {
                        // Save new course
                        courseRepository.save(course);
                        count++;
                    }
                }

                // Wait for 200ms between requests
                Thread.sleep(200);

            } catch (Exception e) {
                System.err.println("Error scraping " + courseSubject + " " + courseNumber + ": " + e.getMessage());
            }
        }

        return count;
    }

    /**
     * Scrapes detailed information for a single course
     */
    private Course scrapeCourseDetail(String url, String subject, String courseNumber, String courseName, Term term) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .ignoreHttpErrors(true)
                .get();

        Course course = new Course(subject, courseNumber, courseName, term);

        // Check if subject is fully supported for detailed parsing
        boolean isFullySupported = FULLY_SUPPORTED_SUBJECTS.contains(subject);

        // Get the main content area
        Element contentTd = doc.selectFirst("td.block_content");
        if (contentTd == null) {
            return course; // Return course with just basic info
        }

        String fullHtml = contentTd.html();
        String fullText = contentTd.text();

        // Extract credits (ALL subjects) - including ranges
        CreditRange creditRange = extractCredits(fullText);
        course.setCredits(creditRange.minCredits);
        course.setMaxCredits(creditRange.maxCredits);

        // Extract SBCs (ALL subjects)
        String sbcs = extractSBCs(fullText);
        course.setSbcsSatisfied(sbcs != null && !sbcs.isEmpty() ? sbcs : "unknown");

        // Extract description (ALL subjects) - same HTML structure for all
        String description = extractDescriptionFromHtml(fullHtml);
        course.setDescription(description);

        // Declare variables outside if-else block
        String prerequisites;
        String corequisites;
        String antirequisites;
        String advisoryPrereqs;
        boolean hasWarning = false;

        if (isFullySupported) {
            // FULLY SUPPORTED: Extract all information
            System.out.println("  [SUPPORTED] Fully supported subject - extracting all information");

            // Extract prerequisites
            Map<String, Object> prereqResult = extractPrerequisites(fullText);
            prerequisites = prereqResult != null ? (String) prereqResult.get("text") : null;
            boolean prereqWarning = prereqResult != null && (Boolean) prereqResult.get("hasWarning");

            // Extract corequisites
            Map<String, Object> coreqResult = extractCorequisites(fullText);
            corequisites = coreqResult != null ? (String) coreqResult.get("text") : null;
            boolean coreqHasWarning = coreqResult != null && (Boolean) coreqResult.get("hasWarning");
            
            // Set warning flag if any placement exam simplification was applied
            hasWarning = prereqWarning || coreqHasWarning;

            // Extract antirequisites
            antirequisites = extractAntirequisites(fullText);

            // Extract advisory prerequisites
            advisoryPrereqs = extractAdvisoryPrerequisites(fullText);
        } else {
            // NOT FULLY SUPPORTED: Mark requisite fields as "unknown" and set warning
            System.out.println("  [WARNING] Subject not fully supported - marking requisite fields as 'unknown'");
            System.out.println("  ✓  Description extracted for unsupported subject");
            prerequisites = "unknown";
            corequisites = "unknown";
            antirequisites = "unknown";
            advisoryPrereqs = "unknown";
            hasWarning = true; // Set warning flag for unsupported subjects
        }

        // Set all fields to course object
        course.setPrerequisites(prerequisites);
        course.setCorequisites(corequisites);
        course.setAntirequisites(antirequisites);
        course.setAdvisoryPrerequisites(advisoryPrereqs);
        course.setWarning(hasWarning);

        // Print comprehensive course summary
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📚 " + subject + " " + courseNumber + " - " + courseName);
        System.out.println("=".repeat(80));
        System.out.println("📖 Description: " + (course.getDescription() != null && course.getDescription().length() > 100 ? course.getDescription().substring(0, 100) + "..." : course.getDescription()));
        
        
        System.out.println("[URL] " + url);
        
        // Field status with icons
        System.out.println("\n[COURSE ATTRIBUTES]");
        System.out.println("  [PREREQ] Prerequisites: " + (course.getPrerequisites() != null && !course.getPrerequisites().isEmpty() && !course.getPrerequisites().equals("unknown") ? "[FOUND] " + course.getPrerequisites() : "[NONE] None"));
        System.out.println("  [COREQ] Corequisites: " + (course.getCorequisites() != null && !course.getCorequisites().isEmpty() && !course.getCorequisites().equals("unknown") ? "[FOUND] " + course.getCorequisites() : "[NONE] None"));
        System.out.println("  [ANTIREQ] Antirequisites: " + (course.getAntirequisites() != null && !course.getAntirequisites().isEmpty() && !course.getAntirequisites().equals("unknown") ? "[FOUND] " + course.getAntirequisites() : "[NONE] None"));
        System.out.println("  [ADV_PREREQ] Advisory Prerequisites: " + (course.getAdvisoryPrerequisites() != null && !course.getAdvisoryPrerequisites().isEmpty() && !course.getAdvisoryPrerequisites().equals("unknown") ? "[FOUND] " + course.getAdvisoryPrerequisites() : "[NONE] None"));
        System.out.println("  [SBC] SBCs: " + (sbcs != null && !sbcs.isEmpty() && !sbcs.equals("unknown") ? "[FOUND] " + sbcs : "[NONE] None"));
        System.out.println("=".repeat(80));

        return course;
    }

    /**
     * Extracts description from HTML (between <hr> and first <strong>)
     */
    private String extractDescriptionFromHtml(String html) {
        // Find content between <hr> and first <br><br><strong>
        int hrIndex = html.indexOf("<hr>");
        if (hrIndex == -1) return null;

        String afterHr = html.substring(hrIndex + 4).trim();
        
        // Find the first <strong> tag which indicates credits or prerequisites
        int strongIndex = afterHr.indexOf("<br>\n<br>\n<strong>");
        if (strongIndex == -1) {
            strongIndex = afterHr.indexOf("<br><br><strong>");
        }
        if (strongIndex == -1) {
            strongIndex = afterHr.indexOf("<strong>");
        }

        String descriptionHtml;
        if (strongIndex > 0) {
            descriptionHtml = afterHr.substring(0, strongIndex).trim();
                    } else {
            descriptionHtml = afterHr;
        }

        // Parse HTML to get clean text
        Document tempDoc = Jsoup.parse(descriptionHtml);
        String description = tempDoc.text().trim();

        // Clean up
        description = description.replaceAll("\\s+", " ");
        
        return description.isEmpty() ? null : description;
    }


    /**
     * Extracts credits, handling both single values and ranges (e.g., "3 credits" or "0-6 credits")
     */
    private CreditRange extractCredits(String text) {
        // Pattern to match credit ranges like "0-6 credits" or single credits like "3 credits"
        Pattern rangePattern = Pattern.compile("(\\d+)-(\\d+)\\s+credits?", Pattern.CASE_INSENSITIVE);
        Matcher rangeMatcher = rangePattern.matcher(text);
        
        if (rangeMatcher.find()) {
            try {
                Integer minCredits = Integer.parseInt(rangeMatcher.group(1));
                Integer maxCredits = Integer.parseInt(rangeMatcher.group(2));
                System.out.println("    [CREDITS] Found credit range: " + minCredits + "-" + maxCredits + " credits");
                return new CreditRange(minCredits, maxCredits);
            } catch (NumberFormatException e) {
                // Fall through to single credit pattern
            }
        }
        
        // Pattern to match single credits like "3 credits"
        Pattern singlePattern = Pattern.compile("(\\d+)\\s+credits?", Pattern.CASE_INSENSITIVE);
        Matcher singleMatcher = singlePattern.matcher(text);
        
        if (singleMatcher.find()) {
            try {
                Integer credits = Integer.parseInt(singleMatcher.group(1));
                System.out.println("    [CREDITS] Found single credit: " + credits + " credits");
                return new CreditRange(credits, null); // maxCredits is null for single values
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        System.out.println("    [CREDITS] No credits found");
        return new CreditRange(null, null);
    }

    /**
     * Extracts prerequisites with placement exam simplification
     * Returns a map with the prerequisites text and whether warning should be set
     */
    private Map<String, Object> extractPrerequisites(String text) {
        StringBuilder allPrereqs = new StringBuilder();
        boolean hasWarning = false;
        
        // First try "Prerequisite(s):" pattern (with parentheses)
        String prereqs = extractField(text, "Prerequisite(s)");
        if (prereqs != null && !prereqs.isEmpty()) {
            // Clean up any "Pre- or" text that might be in the prerequisite field
            prereqs = prereqs.replaceAll("Pre-\\s*or\\s*", "").trim();
            
            if (prereqs.toLowerCase().contains("placement") && prereqs.toLowerCase().contains("exam")) {
                System.out.println("    [WARNING] Simplifying prerequisites with placement exam");
                prereqs = simplifyPlacementExamPrerequisite(prereqs);
                hasWarning = true;
            }
            allPrereqs.append(prereqs);
        }
        
        // Then try regular prerequisite pattern (only if Prerequisite(s) was not found)
        if (prereqs == null || prereqs.isEmpty()) {
            prereqs = extractField(text, "Prerequisite");
            if (prereqs != null && !prereqs.isEmpty()) {
                if (prereqs.toLowerCase().contains("placement") && prereqs.toLowerCase().contains("exam")) {
                    System.out.println("    [WARNING] Simplifying prerequisites with placement exam");
                    prereqs = simplifyPlacementExamPrerequisite(prereqs);
                    hasWarning = true;
                }
                allPrereqs.append(prereqs);
            }
        }
        
        // Also check for "Pre- or corequisite" pattern (but not "Advisory Preq")
        Pattern preOrCoPattern = Pattern.compile("(?<!Advisory\\s)Pre- or corequisite\\s*:\\s*([A-Z0-9\\s,;or]+?)(?=\\s*(?:Back to Top|Print|Repeatable|Offered|DEC|SBC:|Note:|Fees|$))", Pattern.CASE_INSENSITIVE);
        Matcher preOrCoMatcher = preOrCoPattern.matcher(text);
        
        if (preOrCoMatcher.find()) {
            String preOrCo = preOrCoMatcher.group(1).trim();
            System.out.println("    [FOUND] Found Pre- or corequisite: " + preOrCo);
            
            // Remove any remaining "Pre- or" text that might have been captured
            preOrCo = preOrCo.replaceAll("Pre-\\s*or\\s*", "").trim();
            
            if (preOrCo.toLowerCase().contains("placement") && preOrCo.toLowerCase().contains("exam")) {
                System.out.println("    [WARNING] Simplifying Pre- or corequisite with placement exam");
                preOrCo = simplifyPlacementExamPrerequisite(preOrCo);
                hasWarning = true;
                
                // For Pre- or corequisite with placement exam, add to both prerequisites and corequisites
                String currentPrereqs = allPrereqs.toString();
                if (currentPrereqs.isEmpty() || !currentPrereqs.contains(preOrCo)) {
                    if (allPrereqs.length() > 0) {
                        allPrereqs.append("; ");
                    }
                    allPrereqs.append(preOrCo);
                }
            } else {
                // Regular Pre- or corequisite without placement exam
                String currentPrereqs = allPrereqs.toString();
                if (currentPrereqs.isEmpty() || !currentPrereqs.contains(preOrCo)) {
                    if (allPrereqs.length() > 0) {
                        allPrereqs.append("; ");
                    }
                    allPrereqs.append(preOrCo);
                }
            }
        }
        
        String result = allPrereqs.length() > 0 ? allPrereqs.toString() : null;
        if (result != null) {
            // Final cleanup of any "Pre- or" text that might have leaked through
            result = result.replaceAll("Pre-\\s*or\\s*", "").trim();
        }
        
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("text", result);
        resultMap.put("hasWarning", hasWarning);
        return resultMap;
    }

    /**
     * Simplifies prerequisites involving placement exams
     * Handles various patterns:
     * - "MAT 123 or higher, or level 4 or higher on the mathematics placement examination" → "MAT 123 or higher"
     * - "B or higher in MAT 123 or level 5 on the mathematics placement examination" → "B or higher in MAT 123"
     * - "C or higher in AMS 151 or MAT 131, or level 7 on the mathematics placement examination" → "C or higher in AMS 151 or MAT 131"
     */
    private String simplifyPlacementExamPrerequisite(String prereqs) {
        System.out.println("    [SIMPLIFY] Simplifying placement exam prerequisite: " + prereqs);
        
        // Remove placement exam parts (everything after "or level X on the mathematics placement examination")
        // Use case-insensitive pattern to match various capitalizations (with or without comma)
        String simplified = prereqs.replaceAll("(?i)(,\\s*)?or\\s+level\\s+\\d+.*placement.*examination.*$", "");
        
        // Clean up any trailing commas or "or" words
        simplified = simplified.replaceAll(",\\s*$", "").trim();
        simplified = simplified.replaceAll("\\s+or\\s*$", "").trim();
        
        // If the result is empty or too short, try to extract course codes
        if (simplified.length() < 5) {
            // Look for course codes (3 letters + 3 digits) in the original text
            Pattern coursePattern = Pattern.compile("([A-Z]{3}\\s+\\d{3}[^.;]*?)(?:[.;]|$)");
            Matcher matcher = coursePattern.matcher(prereqs);
            
            if (matcher.find()) {
                simplified = matcher.group(1).trim();
            }
        }
        
        System.out.println("    [SUCCESS] Simplified to: " + simplified);
        return simplified;
    }

    /**
     * Extracts corequisites
     * Returns a map with the corequisites text and whether warning should be set
     */
    private Map<String, Object> extractCorequisites(String text) {
        StringBuilder allCoreqs = new StringBuilder();
        boolean hasWarning = false;
        
        // First try "Corequisite(s):" pattern (with parentheses)
        String coreqs = extractField(text, "Corequisite(s)");
        if (coreqs != null && !coreqs.isEmpty()) {
            // Clean up any "Pre- or" text that might be in the corequisite field
            coreqs = coreqs.replaceAll("Pre-\\s*or\\s*", "").trim();
            
            if (coreqs.toLowerCase().contains("placement") && coreqs.toLowerCase().contains("exam")) {
                System.out.println("    [WARNING] Simplifying corequisites with placement exam");
                coreqs = simplifyPlacementExamPrerequisite(coreqs);
                hasWarning = true;
            }
            allCoreqs.append(coreqs);
        }
        
        // Then try regular corequisite pattern
        if (coreqs == null || coreqs.isEmpty()) {
            coreqs = extractField(text, "Corequisite");
            if (coreqs != null && !coreqs.isEmpty()) {
                // Clean up any "Pre- or" text that might be in the corequisite field
                coreqs = coreqs.replaceAll("Pre-\\s*or\\s*", "").trim();
                coreqs = coreqs.replaceAll("Pre-\\s+or\\s*", "").trim();
                coreqs = coreqs.replaceAll("Pre-\\s*or\\s+", "").trim();
                coreqs = coreqs.replaceAll("Pre-\\s*or", "").trim();
                
                if (coreqs.toLowerCase().contains("placement") && coreqs.toLowerCase().contains("exam")) {
                    System.out.println("    [WARNING] Simplifying corequisites with placement exam");
                    coreqs = simplifyPlacementExamPrerequisite(coreqs);
                    hasWarning = true;
                }
                allCoreqs.append(coreqs);
            }
        }
        
        // Also check for "Pre- or corequisite" pattern (but not "Advisory Preq")
        Pattern preOrCoPattern = Pattern.compile("(?<!Advisory\\s)Pre- or corequisite\\s*:\\s*([A-Z0-9\\s,;or]+?)(?=\\s*(?:Back to Top|Print|Repeatable|Offered|DEC|SBC:|Note:|Fees|$))", Pattern.CASE_INSENSITIVE);
        Matcher preOrCoMatcher = preOrCoPattern.matcher(text);
        
        if (preOrCoMatcher.find()) {
            String preOrCo = preOrCoMatcher.group(1).trim();
            System.out.println("    [FOUND] Found Pre- or corequisite (coreq section): " + preOrCo);
            
            // Remove any remaining "Pre- or" text that might have been captured
            preOrCo = preOrCo.replaceAll("Pre-\\s*or\\s*", "").trim();
            
            if (preOrCo.toLowerCase().contains("placement") && preOrCo.toLowerCase().contains("exam")) {
                System.out.println("    [WARNING] Simplifying Pre- or corequisite with placement exam (coreq section)");
                preOrCo = simplifyPlacementExamPrerequisite(preOrCo);
                hasWarning = true;
            }
            
            // Check if this content is already in the corequisites to avoid duplication
            String currentCoreqs = allCoreqs.toString();
            if (currentCoreqs.isEmpty() || !currentCoreqs.contains(preOrCo)) {
                if (allCoreqs.length() > 0) {
                    allCoreqs.append("; ");
                }
                allCoreqs.append(preOrCo);
            }
        }
        
        String result = allCoreqs.length() > 0 ? allCoreqs.toString() : null;
        
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("text", result);
        resultMap.put("hasWarning", hasWarning);
        return resultMap;
    }

    /**
     * Extracts antirequisites
     */
    private String extractAntirequisites(String text) {
        // Try "Antirequisite(s):" pattern (with parentheses)
        Pattern antireqPattern = Pattern.compile("Antirequisite\\(s\\)\\s*:\\s*([^\\r\\n]*?)(?=\\s*(?:Back to Top|Print|Repeatable|SBC:|Offered|DEC|Note:|Fees|$|\\r\\n))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = antireqPattern.matcher(text);
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            return result.isEmpty() ? null : result;
        }
        
        // Try "Anti-requisite:" pattern (with hyphen)
        antireqPattern = Pattern.compile("Anti-requisite\\s*:\\s*([^\\r\\n]*?)(?=\\s*(?:Back to Top|Print|Repeatable|SBC:|Offered|DEC|Note:|Fees|$|\\r\\n))", Pattern.CASE_INSENSITIVE);
        matcher = antireqPattern.matcher(text);
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            return result.isEmpty() ? null : result;
        }
        
        // Try regular "Antirequisite:" pattern (without hyphen)
        antireqPattern = Pattern.compile("Antirequisite\\s*:\\s*([^\\r\\n]*?)(?=\\s*(?:Back to Top|Print|Repeatable|SBC:|Offered|DEC|Note:|Fees|$|\\r\\n))", Pattern.CASE_INSENSITIVE);
        matcher = antireqPattern.matcher(text);
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            return result.isEmpty() ? null : result;
        }
        
        // Only look for "Not for credit" format if it appears as a standalone field (not in description)
        // Must be on its own line or after credits line
        Pattern pattern = Pattern.compile("(?:\\d+\\s+credits?\\s*\\n\\s*|^\\s*)Not for credit in addition to ([^.]+)(?:\\s*\\n|$)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher matcher1 = pattern.matcher(text);
        
        if (matcher1.find()) {
            return matcher1.group(1).trim();
        }
        
        // Try "May not be taken" format - must be on its own line or after credits
        pattern = Pattern.compile("(?:\\d+\\s+credits?\\s*\\n\\s*|^\\s*)May not be taken (?:by students )?with credit for ([^.]+)(?:\\s*\\n|$)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher matcher2 = pattern.matcher(text);
        
        if (matcher2.find()) {
            return matcher2.group(1).trim();
        }
        
        return null;
    }

    /**
     * Extracts advisory prerequisites
     */
    private String extractAdvisoryPrerequisites(String text) {
        // Try "Advisory Preq:" format first (this is the actual format in HTML)
        Pattern pattern = Pattern.compile("Advisory Preq\\s*:\\s*([^\\n\\r]+?)(?=\\s*(?:Back to Top|Print|Repeatable|Offered|DEC|SBC:|Note:|Fees|$))", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            return result.isEmpty() ? null : result;
        }
        
        // Try "Advisory Prerequisite(s):" pattern (with parentheses)
        pattern = Pattern.compile("Advisory Prerequisite\\(s\\)\\s*:\\s*([^\\n\\r]*?)(?=\\s*(?:Back to Top|Print|Repeatable|Offered|DEC|SBC:|Note:|Fees|$))", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            return result.isEmpty() ? null : result;
        }
        
        // Fallback to full "Advisory Prerequisite" format
        pattern = Pattern.compile("Advisory Prerequisite\\s*:\\s*([^\\n\\r]*?)(?=\\s*(?:Back to Top|Print|Repeatable|Offered|DEC|SBC:|Note:|Fees|$))", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            return result.isEmpty() ? null : result;
        }
        
        return null;
    }

    /**
     * Extracts SBCs with improved parsing for all SBC patterns
     */
    private String extractSBCs(String text) {
        // First try "SBC: Partially fulfills: ..." pattern - remove "SBC:" prefix
        Pattern sbcPartialPattern = Pattern.compile("SBC\\s*:\\s*(Partially fulfills\\s*:?\\s*[^\\r\\n]*?)(?=\\s*(?:Back to Top|Print|Repeatable|Note:|Fees|$|\\r\\n))", Pattern.CASE_INSENSITIVE);
        Matcher sbcPartialMatcher = sbcPartialPattern.matcher(text);
        
        if (sbcPartialMatcher.find()) {
            String sbcs = sbcPartialMatcher.group(1).trim();
            // Ensure it has a colon after "Partially fulfills"
            if (!sbcs.contains(":")) {
                sbcs = sbcs.replaceFirst("Partially fulfills", "Partially fulfills:");
            }
            return cleanSBCString(sbcs);
        }
        
        // Then try standalone "Partially fulfills" pattern (without SBC: prefix)
        Pattern partialPattern = Pattern.compile("(Partially fulfills\\s*:?\\s*[^\\r\\n]*?)(?=\\s*(?:Back to Top|Print|Repeatable|Note:|Fees|$|\\r\\n))", Pattern.CASE_INSENSITIVE);
        Matcher partialMatcher = partialPattern.matcher(text);
        
        if (partialMatcher.find()) {
            String sbcs = partialMatcher.group(1).trim();
            // Ensure it has a colon after "Partially fulfills"
            if (!sbcs.contains(":")) {
                sbcs = sbcs.replaceFirst("Partially fulfills", "Partially fulfills:");
            }
            return cleanSBCString(sbcs);
        }
        
        // Try simple SBC pattern - remove "SBC:" prefix for simple SBCs
        Pattern simpleSbcPattern = Pattern.compile("SBC\\s*:\\s*([A-Z+,\\s]+?)(?=\\s*(?:Back to Top|Print|Repeatable|Note:|Fees|$|\\r\\n))", Pattern.CASE_INSENSITIVE);
        Matcher simpleMatcher = simpleSbcPattern.matcher(text);
        
        if (simpleMatcher.find()) {
            String sbcs = simpleMatcher.group(1).trim();
            // Skip if it contains "Partially fulfills" (already handled above)
            if (!sbcs.toLowerCase().contains("partially fulfills")) {
                return cleanSBCString(sbcs);
            }
        }
        
        return null;
    }

    /**
     * Cleans SBC string by removing unwanted suffixes and formatting
     */
    private String cleanSBCString(String sbcString) {
        if (sbcString == null || sbcString.isEmpty()) {
            return null;
        }
        
        // Clean up extra spaces and punctuation first
        sbcString = sbcString.replaceAll("[.,;]+$", "").trim();
        sbcString = sbcString.replaceAll("\\s+", " ").trim();
        
        // Remove unwanted suffixes from the entire string
        sbcString = sbcString.replaceAll("\\s+Back to Top.*$", "");
        sbcString = sbcString.replaceAll("\\s+Repeatable.*$", "");
        sbcString = sbcString.replaceAll("\\s*Print.*$", "");
        sbcString = sbcString.replaceAll("\\s*\\|.*$", "");
        sbcString = sbcString.replaceAll("\\s*\\(opens a new window\\).*$", "");
        
        // Clean up any remaining extra spaces
        sbcString = sbcString.trim();
        
        return sbcString.isEmpty() ? null : sbcString;
    }

    /**
     * Generic field extraction helper
     */
    private String extractField(String text, String fieldName) {
        String patternStr = fieldName + "(?:\\(s\\))?\\s*:\\s*";
        Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        if (!matcher.find()) {
            return null;
        }
        
        int startIndex = matcher.end();
        String remaining = text.substring(startIndex);
        
        // Find the end
        String[] stopKeywords = {
            "Prerequisite", "Corequisite", "Advisory Prerequisite", "Advisory Preq",
            "Antirequisite", "Anti-requisite", "SBC:", "Not for credit", "May not be taken",
            "Repeatable", "Offered", "DEC:", "Note:", "Fees", "\\d+\\s+credits?"
        };
        
        String stopPattern = String.join("|", stopKeywords);
        Pattern endPattern = Pattern.compile("\\s+(" + stopPattern + ")", Pattern.CASE_INSENSITIVE);
        Matcher endMatcher = endPattern.matcher(remaining);
        
        String result;
        if (endMatcher.find()) {
            result = remaining.substring(0, endMatcher.start()).trim();
        } else {
            int periodIndex = remaining.indexOf('.');
            if (periodIndex > 0) {
                result = remaining.substring(0, periodIndex).trim();
            } else {
                result = remaining.trim();
            }
        }
        
        result = result.replaceAll("\\s+", " ").trim();
        result = result.replaceAll("[.,;]+$", "").trim();
        
        // Remove unwanted text patterns
        result = result.replaceAll("\\s*Back to Top.*$", "");
        result = result.replaceAll("\\s*Print-Friendly Page.*$", "");
        result = result.replaceAll("\\s*Print.*$", "");
        result = result.replaceAll("\\s*\\|.*$", "");
        result = result.replaceAll("\\s*\\(opens a new window\\).*$", "");
        result = result.replaceAll("\\s*\\[WARNING:.*$", "");
        result = result.replaceAll("\\s*SBC\\s*:.*$", "");
        result = result.replaceAll("\\s*Advisory Preq\\s*:.*$", "");
        result = result.replaceAll("\\s*Fees.*$", "");
        result = result.replaceAll("\\s*Note:.*$", "");
        result = result.trim();
        
        return result.isEmpty() ? null : result;
    }

    /**
     * Gets or creates a term
     */
    private Term getOrCreateTerm(String termName) {
        Optional<Term> existingTerm = termRepository.findByName(termName);
        if (existingTerm.isPresent()) {
            return existingTerm.get();
        }

        Term newTerm = new Term(termName);

        if (termName.toLowerCase().contains("fall")) {
            String year = extractYearFromTermName(termName);
            if (year != null) {
                newTerm.setStartDate(LocalDate.parse(year + "-08-15"));
                newTerm.setEndDate(LocalDate.parse(year + "-12-15"));
            }
        } else if (termName.toLowerCase().contains("spring")) {
            String year = extractYearFromTermName(termName);
            if (year != null) {
                newTerm.setStartDate(LocalDate.parse(year + "-01-15"));
                newTerm.setEndDate(LocalDate.parse(year + "-05-15"));
            }
        }

        return termRepository.save(newTerm);
    }

    /**
     * Extracts year from term name
     */
    private String extractYearFromTermName(String termName) {
        Pattern yearPattern = Pattern.compile("(\\d{4})");
        Matcher matcher = yearPattern.matcher(termName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Gets courses for a term with fallback to nearest available term
     * If the requested term hasn't been scraped, uses the nearest term with course data
     */
    public List<Course> getCoursesForTerm(String termName, String subject) {
        // First, try to get courses for the exact term
        List<Course> courses = courseRepository.findBySubjectAndTermName(subject, termName);

        if (!courses.isEmpty()) {
            System.out.println("Found " + courses.size() + " courses for term: " + termName + ", subject: " + subject);
            return courses;
        }

        // Check if the term exists but hasn't been scraped
        Optional<Term> termOpt = termRepository.findByName(termName);
        if (termOpt.isPresent() && !termOpt.get().getIsScraped()) {
            System.out.println("Term '" + termName + "' exists but hasn't been scraped yet. Using fallback to nearest available term.");
        }

        // Fallback to nearest term with course data for this subject
        List<Term> availableTerms = termRepository.findTermsWithCoursesForSubject(subject);

        if (!availableTerms.isEmpty()) {
            // Sort by start date to get the most recent term
            availableTerms.sort((t1, t2) -> {
                if (t1.getStartDate() == null && t2.getStartDate() == null) return 0;
                if (t1.getStartDate() == null) return 1;
                if (t2.getStartDate() == null) return -1;
                return t2.getStartDate().compareTo(t1.getStartDate()); // Most recent first
            });
            
            Term nearestTerm = availableTerms.get(0);
            List<Course> fallbackCourses = courseRepository.findBySubjectAndTerm(subject, nearestTerm);
            System.out.println("Using fallback term: " + nearestTerm.getName() + " for subject: " + subject + " (found " + fallbackCourses.size() + " courses)");
            return fallbackCourses;
        }

        System.out.println("No courses found for subject: " + subject + " in any term");
        return new ArrayList<>();
    }
}
