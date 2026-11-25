package com.stdevsec.atlas;

import org.springframework.boot.SpringApplication;

public class TestStAtlasBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(StAtlasBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
