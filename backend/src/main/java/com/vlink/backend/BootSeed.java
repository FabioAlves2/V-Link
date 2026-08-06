//Dummy data just to test
package com.vlink.backend;

import com.vlink.backend.model.Event;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;

//Only going to run when dev profile is active
@Configuration
@Profile("dev")
public class BootSeed {

    //If tables are empty, fill with dummy data
    @Bean
    CommandLineRunner seed(EventRepository events, UserRepository users, PasswordEncoder passwordEncoder){
        return args -> {
            User organizer = users.findByEmail("ana@demo.pt").orElse(null);
            if (organizer == null){
                organizer = new User();
                organizer.setName("Ana Organizer");
                organizer.setEmail("ana@demo.pt");
                organizer.setPassword(passwordEncoder.encode("ana123"));
                organizer.setRole(User.Role.PROMOTER);
                users.save(organizer);
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
                e.setOrganizer(organizer);
                events.save(e);
            }
        };
    }

}
