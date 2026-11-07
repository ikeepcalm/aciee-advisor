package ro.aciee.advisor.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportDataSourceConfig {

    @Bean(defaultCandidate = false)
    @Qualifier("report")
    @ConfigurationProperties("app.report-datasource")
    HikariDataSource reportDataSource() {
        return new HikariDataSource();
    }
}
