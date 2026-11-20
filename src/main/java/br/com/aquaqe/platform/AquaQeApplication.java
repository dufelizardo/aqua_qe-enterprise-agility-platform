package br.com.aquaqe.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AquaQeApplication {

	public static void main(String[] args) {
		SpringApplication.run(AquaQeApplication.class, args);
	}
}
