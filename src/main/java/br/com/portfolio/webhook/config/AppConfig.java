package br.com.portfolio.webhook.config;

import br.com.portfolio.webhook.service.RetryBackoffPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WebhookProperties.class)
public class AppConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** RestClient used to POST webhooks, with strict timeouts so a slow receiver cannot stall the worker. */
    @Bean
    public RestClient webhookRestClient(WebhookProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getDispatcher().getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getDispatcher().getReadTimeoutMs()));
        ClientHttpRequestFactory requestFactory = factory;
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    public RetryBackoffPolicy retryBackoffPolicy(WebhookProperties props) {
        WebhookProperties.Retry retry = props.getRetry();
        return new RetryBackoffPolicy(retry.baseDelay(), retry.maxDelay(), retry.getMaxAttempts());
    }
}
