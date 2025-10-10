package edu.sbu.cse416.sam_system;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import java.sql.Connection;

import javax.sql.DataSource;


@SpringBootApplication
@RestController
public class SamSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SamSystemApplication.class, args);
	}

	@CrossOrigin(origins = "http://localhost:5173")
	@GetMapping("/home")
	public String home() {
		return "Spring Boot";
	}

 	@Bean
	public CommandLineRunner databaseConnectionChecker(DataSource dataSource) {
        return args -> {
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
