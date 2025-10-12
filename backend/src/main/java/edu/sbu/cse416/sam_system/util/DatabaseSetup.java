package edu.sbu.cse416.sam_system.util;

/*
 * TP: Used Cursor to implement database reset utility for SAM system
 * Features:
 * - Creates 'sam' database if it doesn't exist
 * - Drops existing 'courses' and 'terms' tables for clean slate
 * - Standalone Java application (can be run independently)
 * - Useful for testing and re-scraping scenarios
 * Usage: Right-click -> Run As -> Java Application
 *        OR: java -cp target/classes;mysql-connector.jar edu.sbu.cse416.sam_system.util.DatabaseSetup
 */

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility to reset the SAM database
 * Run this when you need to completely clear all scraped data
 * 
 * Usage: Right-click -> Run As -> Java Application
 */
public class DatabaseSetup {

    private static final String DB_URL_BASE = "jdbc:mysql://localhost:3306/";
    private static final String DB_NAME = "sam";
    private static final String USER = "root";
    private static final String PASS = "Mm10194105828!21";

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("SAM DATABASE RESET UTILITY");
        System.out.println("=".repeat(80));

        try (Connection conn = DriverManager.getConnection(DB_URL_BASE, USER, PASS)) {
            System.out.println("\n✓ Connected to MySQL server");
            
            // Create database if not exists
            try (Statement stmt = conn.createStatement()) {
                System.out.println("✓ Creating database '" + DB_NAME + "' if it doesn't exist...");
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
                System.out.println("✓ Database '" + DB_NAME + "' is ready");
            }

            // Connect to the specific database and drop tables
            try (Connection dbConn = DriverManager.getConnection(DB_URL_BASE + DB_NAME, USER, PASS);
                 Statement stmt = dbConn.createStatement()) {
                
                System.out.println("\n✓ Dropping existing tables...");
                stmt.executeUpdate("DROP TABLE IF EXISTS courses");
                System.out.println("  • Dropped 'courses' table");
                stmt.executeUpdate("DROP TABLE IF EXISTS terms");
                System.out.println("  • Dropped 'terms' table");
            }

            System.out.println("\n" + "=".repeat(80));
            System.out.println("DATABASE RESET COMPLETED SUCCESSFULLY!");
            System.out.println("=".repeat(80));
            System.out.println("\nNext steps:");
            System.out.println("1. Run the Spring Boot application (it will recreate tables)");
            System.out.println("2. Use POST /api/scraping/scrape to scrape courses");
            System.out.println("\nExample Postman request:");
            System.out.println("POST http://localhost:8080/api/scraping/scrape");
            System.out.println("Body: { \"term\": \"Fall 2025\", \"subjects\": [\"CSE\", \"BIO\"] }");

        } catch (SQLException e) {
            System.err.println("\n" + "=".repeat(80));
            System.err.println("ERROR: Database reset failed!");
            System.err.println("=".repeat(80));
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

