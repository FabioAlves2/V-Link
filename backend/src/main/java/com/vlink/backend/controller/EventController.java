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

    @GetMapping("/{id}")
    public ResponseEntity<Event> get(@PathVariable Long id){
        return repo.findById(id).map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Event> update(@PathVariable Long id, @RequestBody Event in){
        return repo.findById(id).map(e -> {
            if (in.getTitle() != null && !in.getTitle().isBlank()) e.setTitle(in.getTitle());
            if (in.getDescription() != null) e.setDescription(in.getDescription());
            if (in.getLocation() != null) e.setLocation(in.getLocation());
            if (in.getStartDate() != null) e.setStartDate(in.getStartDate());
            if (in.getEndDate() != null) e.setEndDate(in.getEndDate());
            if (in.getCapacity() > 0) e.setCapacity(in.getCapacity());
            if (in.getStatus() != null) e.setStatus(in.getStatus());
            return ResponseEntity.ok(repo.save(e));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

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
