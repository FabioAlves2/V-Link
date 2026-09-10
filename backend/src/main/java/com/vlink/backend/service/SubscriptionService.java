package com.vlink.backend.service;

import com.vlink.backend.model.Subscription;
import com.vlink.backend.repo.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepo;

    // REQUIRES_NEW, não a transação já aberta em SubscriptionController.subscribe() (que
    // detém o lock pessimista do evento e tem de sobreviver mesmo que esta escrita específica
    // falhe). Sem try/catch aqui dentro, de propósito: uma vez que uma violação de constraint
    // acontece, o EntityManager/Session desta transação fica permanentemente inválido para a
    // especificação JPA — apanhar a exceção e tentar fazer commit na mesma falha sempre com
    // UnexpectedRollbackException, mesmo numa transação já isolada por REQUIRES_NEW. A única
    // forma correta de recuperar é deixar ESTA transação fazer rollback (o resultado normal de
    // deixar a exceção propagar através da fronteira de @Transactional) e apanhar a exceção
    // já fora dela, em SubscriptionController.subscribe() — nessa altura a transação exterior
    // (suspensa por REQUIRES_NEW, nunca tocada por esta falha) continua saudável.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySave(Subscription sub) {
        subscriptionRepo.save(sub);
    }
}
