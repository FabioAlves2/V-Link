package com.vlink.backend.repo;

import com.vlink.backend.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserEmail(String email);
    boolean existsByUserEmailAndEventId(String email, Long eventId);
    long countByEventId(Long eventId);

    @Transactional
    void deleteByUserEmailAndEventId(String email, Long eventId);
}
