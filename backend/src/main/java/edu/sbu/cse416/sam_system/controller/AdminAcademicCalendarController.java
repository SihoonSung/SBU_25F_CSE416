package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.AcademicEvent;
import edu.sbu.cse416.sam_system.service.AcademicCalendarService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/admin/academic-calendar")
public class AdminAcademicCalendarController {

    private final AcademicCalendarService service;
    public AdminAcademicCalendarController(AcademicCalendarService service) {
        this.service = service;
    }

    @GetMapping
    public List<AcademicEvent> list() {
        return service.list();
    }

    @PostMapping("/import")
    public Map<String, Object> importYaml(@RequestParam("file") MultipartFile file) throws Exception {
        return service.importYaml(file);
    }

    @DeleteMapping
    public void clearAll() {
        service.clearAll();
    }
}