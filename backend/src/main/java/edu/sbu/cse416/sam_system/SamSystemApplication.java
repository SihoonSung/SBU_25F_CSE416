package edu.sbu.cse416.sam_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;



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





}
