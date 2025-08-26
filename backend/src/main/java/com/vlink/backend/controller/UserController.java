package com.vlink.backend.controller;

import com.vlink.backend.model.User;
import com.vlink.backend.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Expose REST endpoints
@RestController
// Define endpoints prefix
@RequestMapping("/api/users")
//Generate constructor with required args
@RequiredArgsConstructor
public class UserController {
    
    private final UserRepository repo;

    // GET /api/users
    @GetMapping
    public List<User> all(){return repo.findAll();}

    // POST /api/users
    @PostMapping
    public ResponseEntity<User> create(@RequestBody User u){
        if (u.getName() == null || u.getName().isBlank()) { 
            // 400 Bad Request
            return ResponseEntity.badRequest().build(); 
        } 
        // 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(u));
    }
}
