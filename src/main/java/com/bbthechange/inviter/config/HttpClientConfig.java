package com.bbthechange.inviter.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpClientConfig {

    private final ExternalParserProperties properties;

    public HttpClientConfig(ExternalParserProperties properties) {
        this.properties = properties;
    }

    @Bean("externalRestTemplate")
    public RestTemplate externalRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectionTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        
        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }
}