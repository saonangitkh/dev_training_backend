package com.devtraining.tickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EventTicketsApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventTicketsApplication.class, args);
	}

}
