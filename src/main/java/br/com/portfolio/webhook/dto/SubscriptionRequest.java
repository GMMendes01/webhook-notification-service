package br.com.portfolio.webhook.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SubscriptionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2000) String webhookUrl,
        List<String> eventTypes,
        boolean emailNotify,
        @Email @Size(max = 255) String emailAddress
) {
}
