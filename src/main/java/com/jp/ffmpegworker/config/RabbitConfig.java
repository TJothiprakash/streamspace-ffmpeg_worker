package com.jp.ffmpegworker.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class RabbitConfig {

    public static final String TRANSCODE_QUEUE = "transcode.queue";

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private int port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host:/}")
    private String virtualHost;

    @Value("${spring.rabbitmq.ssl.enabled:false}")
    private boolean sslEnabled;

    @Bean
    public CachingConnectionFactory connectionFactory() throws NoSuchAlgorithmException, KeyManagementException {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setVirtualHost(virtualHost);

        if (sslEnabled) {
            factory.getRabbitConnectionFactory().useSslProtocol();
            System.out.println("✅ SSL enabled for RabbitMQ");
        }

        System.out.printf("🐇 RabbitMQ connected: %s:%d [%s]%n", host, port, virtualHost);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        return new RabbitTemplate(connectionFactory);
    }

    @Bean
    public Queue transcodeQueue() {
        return new Queue(TRANSCODE_QUEUE, true);
    }
}
