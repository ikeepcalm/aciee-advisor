package ro.aciee.advisor;

import org.springframework.boot.SpringApplication;

public class TestAdvisorApplication {

	public static void main(String[] args) {
		SpringApplication.from(AdvisorApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
