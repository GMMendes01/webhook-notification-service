package br.com.portfolio.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebhookNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookNotificationServiceApplication.class, args);
    }
}
