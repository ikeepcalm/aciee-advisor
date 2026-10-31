package ro.aciee.advisor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"))
				.withCopyFileToContainer(MountableFile.forHostPath("../infra/postgres/init-roles.sh"), "/docker-entrypoint-initdb.d/init-roles.sh")
				.withEnv("ACIEE_IDENT_PASSWORD", "aciee_ident")
				.withEnv("ACIEE_ANON_PASSWORD", "aciee_anon")
				.withEnv("ACIEE_REPORT_PASSWORD", "aciee_report");
	}

	@Bean
	DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres) {
		return registry -> {
			registry.add("spring.datasource.url", postgres::getJdbcUrl);
			registry.add("spring.liquibase.user", postgres::getUsername);
			registry.add("spring.liquibase.password", postgres::getPassword);
		};
	}

}
