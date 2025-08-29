package com.vlink.backend.controller;

import com.vlink.backend.model.Event;
import com.vlink.backend.repo.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin("http://localhost:5173/")
// Expose REST endpoints
@RestController
// Define endpoints prefix
@RequestMapping("/api/events")
//Generate constructor with required args
@RequiredArgsConstructor
public class EventController {
    
    private final EventRepository repo;

    // GET /api/events
    @GetMapping
    public List<Event> all(){return repo.findAll();}

    // POST /api/events
    @PostMapping
    public ResponseEntity<Event> create(@RequestBody Event e){
        if (e.getTitle() == null || e.getTitle().isBlank()) { 
            // 400 Bad Request
            return ResponseEntity.badRequest().build(); 
        } 
        if (e.getCapacity() < 1) {
            e.setCapacity(1); 
        }
        // 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(e));
    }
}
