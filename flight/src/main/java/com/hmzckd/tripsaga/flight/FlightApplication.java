package com.hmzckd.tripsaga.flight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.hmzckd.tripsaga.faults.FaultsConfiguration;

@SpringBootApplication
@Import(FaultsConfiguration.class)
public class FlightApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlightApplication.class, args);
	}
}
