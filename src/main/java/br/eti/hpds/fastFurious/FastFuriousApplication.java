package br.eti.hpds.fastFurious;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class FastFuriousApplication {

    public static void main(String[] args) {
        SpringApplication.run(FastFuriousApplication.class, args);
    }

    // Adicione este bean para suprir a dependência exigida pelo CieloTerminal
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

}