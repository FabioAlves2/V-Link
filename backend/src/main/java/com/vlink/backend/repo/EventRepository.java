package com.vlink.backend.repo;

import com.vlink.backend.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

//Give the generic the Entity and the Primary Key
//This generates generic queries like findAll, save, delete, count
public interface EventRepository extends JpaRepository<Event, Long> {}
