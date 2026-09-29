package br.com.portfolio.webhook.repository;

import br.com.portfolio.webhook.model.Subscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByActiveTrue();

    boolean existsByName(String name);
}
