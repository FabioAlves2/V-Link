package com.vlink.backend.repo;

import com.vlink.backend.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserEmail(String email);
    boolean existsByUserEmailAndEventId(String email, Long eventId);
    long countByEventId(Long eventId);
    List<Subscription> findByEventId(Long eventId);
    Optional<Subscription> findByEventIdAndUserId(Long eventId, Long userId);

    @Transactional
    void deleteByUserEmailAndEventId(String email, Long eventId);

    @Transactional
    void deleteByEventId(Long eventId);
}
