package edu.sbu.cse416.sam_system;

import edu.sbu.cse416.sam_system.service.ScrapingService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScrapingTest {
    
    public static void main(String[] args) {
        System.out.println("=== Testing SBU Course Catalog Scraping ===");
        
        try {
            // Test direct connection to the catalog
            String catalogUrl = "https://catalog.stonybrook.edu/content.php";
            String subject = "CSE";
            
            System.out.println("Connecting to: " + catalogUrl);
            
            Document doc = Jsoup.connect(catalogUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(30000)
                    .get();
            
            System.out.println("Successfully connected to catalog");
            System.out.println("Page title: " + doc.title());
            
            // Look for CSE content
            Elements cseElements = doc.select("*:contains(CSE)");
            System.out.println("Found " + cseElements.size() + " elements containing 'CSE'");
            
            // Look for course patterns
            String pageText = doc.text();
            Pattern coursePattern = Pattern.compile("(CSE)\\s+(\\d{3,4})\\s*[-:]?\\s*(.+)");
            Matcher matcher = coursePattern.matcher(pageText);
            
            int courseCount = 0;
            while (matcher.find() && courseCount < 10) {
                String foundSubject = matcher.group(1);
                String courseNumber = matcher.group(2);
                String courseName = matcher.group(3).trim();
                
                // Clean up course name
                if (courseName.contains(".")) {
                    courseName = courseName.substring(0, courseName.indexOf("."));
                }
                
                System.out.println("Found course: " + foundSubject + " " + courseNumber + " - " + courseName);
                courseCount++;
            }
            
            System.out.println("Total CSE courses found: " + courseCount);
            
            // Try to find table structures
            Elements tables = doc.select("table");
            System.out.println("Found " + tables.size() + " tables");
            
            for (int i = 0; i < Math.min(3, tables.size()); i++) {
                Element table = tables.get(i);
                Elements rows = table.select("tr");
                System.out.println("Table " + i + " has " + rows.size() + " rows");
                
                // Look at first few rows
                for (int j = 0; j < Math.min(3, rows.size()); j++) {
                    Element row = rows.get(j);
                    String rowText = row.text();
                    if (rowText.contains("CSE")) {
                        System.out.println("Row " + j + ": " + rowText.substring(0, Math.min(200, rowText.length())));
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error during scraping test: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== End Test ===");
    }
}
