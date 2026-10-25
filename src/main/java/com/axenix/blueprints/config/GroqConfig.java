package com.axenix.blueprints.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class GroqConfig {
    @Bean("groqRestClient")
    public RestClient groqRestClient(
            @Value("${groq.base-url:https://groq.com}") String baseUrl,
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${groq.read-timeout-ms:90000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        System.out.println("Groq configured base URL: " + baseUrl);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    System.out.println(
                            "Groq request: "
                                    + request.getMethod()
                                    + " "
                                    + request.getURI()
                    );

                    return execution.execute(request, body);
                })
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                );

        if (!apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }

        return builder.build();
    }
}