package com.vlink.backend.repo;

import com.vlink.backend.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserEmail(String email);
    boolean existsByUserEmailAndEventId(String email, Long eventId);

    @Transactional
    void deleteByUserEmailAndEventId(String email, Long eventId);

    @Transactional
    void deleteByEventId(Long eventId);
}
