//Dummy data just to test
package com.vlink.backend;

import com.vlink.backend.model.Event;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import java.time.LocalDateTime;

//Only going to run when dev profile is active
@Configuration
@Profile("dev")
public class BootSeed {
    
    //If tables are empty, fill with dummy data
    @Bean
    CommandLineRunner seed(EventRepository events, UserRepository users){
        return args -> {
            if (users.count() == 0){
                User u = new User();
                u.setName("Ana Organizer");
                u.setEmail("ana@demo.pt");
                u.setPassword("ana123");
                u.setRole(User.Role.PROMOTER);
                users.save(u);
            }
            if (events.count()==0){
                Event e = new Event();
                e.setTitle("Praia Limpa");
                e.setDescription("Mutirão de limpeza em Matosinhos");
                e.setLocation("Matosinhos");
                e.setStartDate(LocalDateTime.now().plusDays(7));
                e.setEndDate(LocalDateTime.now().plusDays(7).plusHours(3));
                e.setCapacity(50);
                e.setStatus(Event.Status.PUBLISHED);
                events.save(e);
            }
        };
    }

}
