package com.prabhakar.orbit;

import org.springframework.boot.SpringApplication;

public class TestOrbitEngineApplication {

	public static void main(String[] args) {
		SpringApplication.from(OrbitEngineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
