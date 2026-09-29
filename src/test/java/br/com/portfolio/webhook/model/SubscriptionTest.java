package br.com.portfolio.webhook.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private static Subscription withTypes(String... types) {
        return Subscription.builder().eventTypes(types).build();
    }

    @Test
    @DisplayName("an empty event-type filter subscribes to everything")
    void emptyFilterMatchesAll() {
        assertThat(withTypes().subscribesTo("order.paid")).isTrue();
        assertThat(withTypes(new String[0]).subscribesTo("anything")).isTrue();
    }

    @Test
    @DisplayName("only listed event types match")
    void filtersByType() {
        Subscription s = withTypes("order.paid", "user.created");

        assertThat(s.subscribesTo("order.paid")).isTrue();
        assertThat(s.subscribesTo("user.created")).isTrue();
        assertThat(s.subscribesTo("order.shipped")).isFalse();
    }

    @Test
    @DisplayName("event-type matching is case-insensitive")
    void caseInsensitive() {
        assertThat(withTypes("Order.Paid").subscribesTo("order.paid")).isTrue();
    }
}
