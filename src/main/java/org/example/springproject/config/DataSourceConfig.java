package org.example.springproject.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Primary
    @Bean(name = "inventoryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.inventory")
    public DataSource inventoryDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "orderDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.order")
    public DataSource orderDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Primary
    @Bean(name = "inventoryJdbcTemplate")
    public JdbcTemplate inventoryJdbcTemplate(
            @Qualifier("inventoryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "orderJdbcTemplate")
    public JdbcTemplate orderJdbcTemplate(
            @Qualifier("orderDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Provides a map of all JdbcTemplates to the RollbackManager and DatabaseWrapper.
     */
    @Bean
    public Map<String, JdbcTemplate> jdbcTemplates(
            @Qualifier("inventoryJdbcTemplate") JdbcTemplate inventoryJdbcTemplate,
            @Qualifier("orderJdbcTemplate") JdbcTemplate orderJdbcTemplate) {
        Map<String, JdbcTemplate> map = new HashMap<>();
        map.put("inventory", inventoryJdbcTemplate);
        map.put("order", orderJdbcTemplate);
        return map;
    }
}
