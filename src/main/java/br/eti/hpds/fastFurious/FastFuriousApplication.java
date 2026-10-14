package br.eti.hpds.fastFurious;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // necessário para o PagamentoPoller
public class FastFuriousApplication {

	public static void main(String[] args) {
		SpringApplication.run(FastFuriousApplication.class, args);
	}

}