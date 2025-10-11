package edu.sbu.cse416.sam_system.config;

import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Test data loader to initialize the database with sample class data.
 * Runs automatically when the application starts.
 * Only loads data if the database is empty.
 * 
 * Note: This is a temporary solution for development/testing.
 * In production, data should be loaded from YAML or SQL files.
 * 
 * TP: Used Cursor to create sample data loader for testing.
 */
@Component
public class TestDataLoader implements CommandLineRunner {
    
    @Autowired
    private ClassRepository classRepository;
    
    @Override
    public void run(String... args) throws Exception {
        // Check if data already exists
        if (classRepository.count() > 0) {
            System.out.println("Database already contains data. Skipping initialization.");
            return;
        }
        
        // Load sample class data for Spring 2025
        ClassEntity class1 = new ClassEntity(
            "CSE", 101, "01", "Spring", 2025, 3,
            "Computer Science Principles",
            "Jalaa Hoblos",
            "Mon/Wed 11:00 AM - 12:20 PM",
            "Computer Science Building 2120",
            "None"
        );
        
        ClassEntity class2 = new ClassEntity(
            "CSE", 101, "02", "Spring", 2025, 3,
            "Computer Science Principles",
            "John Smith",
            "Tue/Thu 9:30 AM - 10:50 AM",
            "Computer Science Building 2125",
            "None"
        );
        
        ClassEntity class3 = new ClassEntity(
            "CSE", 214, "01", "Spring", 2025, 3,
            "Data Structures",
            "Paul Fodor",
            "Mon/Wed/Fri 1:00 PM - 2:30 PM",
            "Frey Hall 317",
            "CSE 114"
        );
        
        ClassEntity class4 = new ClassEntity(
            "AMS", 261, "01", "Spring", 2025, 3,
            "Applied Calculus II",
            "Jane Doe",
            "Tue/Thu 11:00 AM - 12:20 PM",
            "Mathematics Building 1002",
            "AMS 151"
        );
        
        ClassEntity class5 = new ClassEntity(
            "CSE", 220, "01", "Spring", 2025, 3,
            "Systems Fundamentals I",
            "Richard McKenna",
            "Mon/Wed 2:00 PM - 3:20 PM",
            "Computer Science 2120",
            "CSE 214"
        );
        
        ClassEntity class6 = new ClassEntity(
            "CSE", 355, "01", "Spring", 2025, 3,
            "Computational Geometry",
            "Joe Mitchell",
            "Mon/Wed 11:00 AM - 12:20 PM",
            "Javits Lecture Hall",
            "AMS 301; programming knowledge of C or C++ or Java"
        );
        
        ClassEntity class7 = new ClassEntity(
            "CSE", 373, "01", "Spring", 2025, 3,
            "Data Structures and Algorithms",
            "Richard McKenna",
            "Tue/Thu 2:00 PM - 3:20 PM",
            "Frey Hall 317",
            "CSE 114; CSE 214"
        );
        
        ClassEntity class8 = new ClassEntity(
            "CSE", 312, "01", "Spring", 2025, 3,
            "Programming Languages",
            "Paul Fodor",
            "Mon/Wed 3:30 PM - 4:50 PM",
            "Computer Science 312B",
            "CSE 214; CSE 373"
        );
        
        ClassEntity class9 = new ClassEntity(
            "AMS", 310, "01", "Spring", 2025, 3,
            "Survey of Probability and Statistics",
            "Y. Yu",
            "Thu 11:00 AM - 12:20 PM",
            "Humanities 1006",
            "AMS 151 or MAT 125"
        );
        
        classRepository.save(class1);
        classRepository.save(class2);
        classRepository.save(class3);
        classRepository.save(class4);
        classRepository.save(class5);
        classRepository.save(class6);
        classRepository.save(class7);
        classRepository.save(class8);
        classRepository.save(class9);
        
        System.out.println("Sample class data loaded successfully for Spring 2025!");
    }
}

