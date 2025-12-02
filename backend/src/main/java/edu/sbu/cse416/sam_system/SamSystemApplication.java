package edu.sbu.cse416.sam_system;

/*
 * TP: Used Cursor to implement main Spring Boot application for SAM system
 * Features:
 * - Main entry point for Spring Boot application
 * - Database connection validation on startup
 * - Basic API endpoints (/home, /api/test) for connectivity testing
 * - CORS configuration for frontend integration (http://localhost:5173)
 * - Scraping functionality accessible via ScrapingController REST API
 */
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;
import java.sql.Connection;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream; 


@SpringBootApplication
@RestController
public class SamSystemApplication {

	public static void main(String[] args) {
		// TP: Used cursor to launch Spring Boot application
		// Load .env file and set system properties
		loadEnvFile("/Users/yoonjaeseong/SBU_25F_CSE416/backend/.env");
		
		SpringApplication.run(SamSystemApplication.class, args);
	}
	
	private static void loadEnvFile(String envFilePath) {
		Path envFile = Paths.get(envFilePath);
		if (!Files.exists(envFile)) {
			System.err.println("Warning: .env file not found at: " + envFilePath);
			return;
		}
		
		try (Stream<String> lines = Files.lines(envFile)) {
			lines.filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
				.forEach(line -> {
					int equalsIndex = line.indexOf('=');
					if (equalsIndex > 0) {
						String key = line.substring(0, equalsIndex).trim();
						String value = line.substring(equalsIndex + 1).trim();
						// Remove quotes if present
						if ((value.startsWith("\"") && value.endsWith("\"")) ||
							(value.startsWith("'") && value.endsWith("'"))) {
							value = value.substring(1, value.length() - 1);
						}
						System.setProperty(key, value);
					}
				});
		} catch (IOException e) {
			System.err.println("Error loading .env file: " + e.getMessage());
		}
	}

	@CrossOrigin(origins = "http://localhost:5173")
	@GetMapping("/home")
	public String home() {
		// TP: Used cursor to provide simple connectivity endpoint
		return "Spring Boot";
	}
	
	@CrossOrigin(origins = "http://localhost:5173")
	@GetMapping("/api/test")
	public String apiTest() {
		// TP: Used cursor to provide simple API test endpoint
		return "API is working!";
	}

    @Bean
	public CommandLineRunner databaseConnectionChecker(DataSource dataSource) {
		// TP: Used cursor to validate database connectivity at startup
        return unused -> {
            try (Connection connection = dataSource.getConnection()) {
                if (connection != null) {
                    System.out.println("=========================================");
                    System.out.println("SUCCESS: Database connection is successful!");
                    System.out.println("Database URL: " + connection.getMetaData().getURL());
                    System.out.println("=========================================");
                }
            } catch (Exception e) {
                System.err.println("=========================================");
                System.err.println("FAILURE: Database connection failed!");
                System.err.println("Please check your application.properties file and MySQL server status.");
                System.err.println("Error Message: " + e.getMessage());
                System.err.println("=========================================");
            }
        };
    }


}
