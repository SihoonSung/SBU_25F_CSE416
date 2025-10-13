package edu.sbu.cse416.sam_system.service;

/*
 * TP: Used Cursor to implement web scraping service for SBU course catalog
 * Features:
 * - Two-step scraping: course list page → individual course detail pages
 * - Extracts all required fields: description, credits, prerequisites, corequisites, antirequisites, advisory prereqs, SBCs
 * - Prevents duplicate entries (updates existing courses if found)
 * - Enforces one-time scraping per term (throws exception if term already scraped)
 * - Handles placement exam simplification with warnings
 * - Supports multiple subjects (BIO, PSY, CSE, ECO, AMS, POL)
 * - Enhanced fallback mechanism: uses nearest available term's data if requested term not scraped
 * - No data deletion on re-scraping: maintains data integrity by preventing re-scraping
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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScrapingService {

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private TermRepository termRepository;

    private static final String CATALOG_BASE_URL = "https://catalog.stonybrook.edu/content.php?catoid=7&navoid=225";
    private static final String COURSE_DETAIL_BASE_URL = "https://catalog.stonybrook.edu/preview_course_nopop.php";

    /**
     * Scrapes courses for specified subjects and term from Stony Brook course catalog
     * Each term can only be scraped once to prevent data inconsistency
     */
    @Transactional
    public void scrapeCourses(String termName, List<String> subjects) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COURSE SCRAPING START");
        System.out.println("Term: " + termName);
        System.out.println("Subjects: " + subjects);
        System.out.println("=".repeat(80));

        // Get or create term
        Term term = getOrCreateTerm(termName);
        
        // Prevent re-scraping: each term can only be scraped once (regardless of subjects)
        if (term.getIsScraped() != null && term.getIsScraped()) {
            throw new IllegalStateException("Term '" + termName + "' has already been scraped. Each term can only be scraped once.");
        }
        
        // Additional safety check: if there are already courses for this term, prevent scraping
        long existingCourseCount = courseRepository.countByTerm(term);
        if (existingCourseCount > 0) {
            throw new IllegalStateException("Term '" + termName + "' already has " + existingCourseCount + " courses. Cannot re-scrape. Use reset-term endpoint if needed.");
        }

        int totalScraped = 0;
        for (String subject : subjects) {
            try {
                System.out.println("\n" + "-".repeat(80));
                System.out.println("Scraping subject: " + subject);
                System.out.println("-".repeat(80));

                int coursesScraped = scrapeSubject(subject, term);
                totalScraped += coursesScraped;

                System.out.println("✓ Scraped " + coursesScraped + " courses for " + subject);

            } catch (Exception e) {
                System.err.println("✗ Error scraping " + subject + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Mark term as scraped
        term.setIsScraped(true);
        termRepository.save(term);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("COURSE SCRAPING COMPLETED");
        System.out.println("Total courses scraped: " + totalScraped);
        System.out.println("=".repeat(80) + "\n");
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
                            existing.setPrerequisites(course.getPrerequisites());
                            existing.setCorequisites(course.getCorequisites());
                            existing.setAntirequisites(course.getAntirequisites());
                            existing.setAdvisoryPrerequisites(course.getAdvisoryPrerequisites());
                            existing.setSbcsSatisfied(course.getSbcsSatisfied());
                            courseRepository.save(existing);
                            System.out.println(String.format("\n[%d] UPDATED: %s %s - %s", 
                                count + 1, course.getSubject(), course.getCourseNumber(), course.getCourseName()));
                        }
                    } else {
                        // Save new course
                        courseRepository.save(course);
                        count++;
                        System.out.println(String.format("\n[%d] NEW: %s %s - %s", 
                            count, course.getSubject(), course.getCourseNumber(), course.getCourseName()));
                    }

                    // Print details
                    System.out.println("    Credits: " + course.getCredits());
                    if (course.getDescription() != null) {
                        String desc = course.getDescription();
                        System.out.println("    Description: " + desc.substring(0, Math.min(60, desc.length())) + "...");
                    }
                    if (course.getPrerequisites() != null) {
                        System.out.println("    Prerequisites: " + course.getPrerequisites().substring(0, Math.min(40, course.getPrerequisites().length())) + "...");
                    }
                    if (course.getSbcsSatisfied() != null) {
                        System.out.println("    SBCs: " + course.getSbcsSatisfied());
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

        // Get the main content area
        Element contentTd = doc.selectFirst("td.block_content");
        if (contentTd == null) {
            return course; // Return course with just basic info
        }

        String fullHtml = contentTd.html();
        String fullText = contentTd.text();

        // Extract description - everything between <hr> and <strong>
        String description = extractDescriptionFromHtml(fullHtml);
        course.setDescription(description);

        // Extract credits
        Integer credits = extractCredits(fullText);
        course.setCredits(credits);

        // Extract prerequisites
        String prerequisites = extractPrerequisites(fullText);
        course.setPrerequisites(prerequisites);

        // Extract corequisites
        String corequisites = extractCorequisites(fullText);
        course.setCorequisites(corequisites);

        // Extract antirequisites
        String antirequisites = extractAntirequisites(fullText);
        course.setAntirequisites(antirequisites);

        // Extract advisory prerequisites
        String advisoryPrereqs = extractAdvisoryPrerequisites(fullText);
        course.setAdvisoryPrerequisites(advisoryPrereqs);

        // Extract SBCs
        String sbcs = extractSBCs(fullText);
        course.setSbcsSatisfied(sbcs);

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
     * Extracts credits
     */
    private Integer extractCredits(String text) {
        Pattern pattern = Pattern.compile("(\\d+)(?:-\\d+)?\\s+credits?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        return null;
    }

    /**
     * Extracts prerequisites with placement exam simplification
     */
    private String extractPrerequisites(String text) {
        String prereqs = extractField(text, "Prerequisite");
        
        if (prereqs != null && !prereqs.isEmpty()) {
            if (prereqs.toLowerCase().contains("placement") && prereqs.toLowerCase().contains("exam")) {
                System.out.println("    ⚠ WARNING: Simplifying prerequisites with placement exam");
                prereqs = simplifyPlacementExamPrerequisite(prereqs);
            }
        }
        
        return prereqs;
    }

    /**
     * Simplifies prerequisites involving placement exams
     */
    private String simplifyPlacementExamPrerequisite(String prereqs) {
        // Look for course codes (3 letters + 3 digits)
        Pattern coursePattern = Pattern.compile("([A-Z]{3}\\s+\\d{3}[^.;]*?)(?:[.;]|$)");
        Matcher matcher = coursePattern.matcher(prereqs);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return prereqs + " [WARNING: Contains placement exam requirement]";
    }

    /**
     * Extracts corequisites
     */
    private String extractCorequisites(String text) {
        return extractField(text, "Corequisite");
    }

    /**
     * Extracts antirequisites
     */
    private String extractAntirequisites(String text) {
        String antireqs = extractField(text, "Antirequisite");
        
        if (antireqs != null && !antireqs.isEmpty()) {
            return antireqs;
        }
        
        // Try "Not for credit" format
        Pattern pattern = Pattern.compile("Not for credit in addition to ([^.]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Try "May not be taken" format
        pattern = Pattern.compile("May not be taken (?:by students )?with credit for ([^.]+)", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }

    /**
     * Extracts advisory prerequisites
     */
    private String extractAdvisoryPrerequisites(String text) {
        return extractField(text, "Advisory Prerequisite");
    }

    /**
     * Extracts SBCs
     */
    private String extractSBCs(String text) {
        Pattern pattern = Pattern.compile("SBC\\s*:\\s*([A-Z, ]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String sbcs = matcher.group(1).trim();
            sbcs = sbcs.replaceAll("[.,;]+$", "").trim();
            return sbcs;
        }
        
        return null;
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
            "Prerequisite", "Corequisite", "Advisory Prerequisite", 
            "Antirequisite", "SBC:", "Not for credit", "May not be taken",
            "\\d+\\s+credits?"
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
