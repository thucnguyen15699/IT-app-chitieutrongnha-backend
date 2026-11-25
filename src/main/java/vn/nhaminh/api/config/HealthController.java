package vn.nhaminh.api.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Docker/Cloudflare kiểm tra app còn sống và nối được database. Không lộ thông tin nhạy cảm. */
@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final String environment;

    public HealthController(JdbcTemplate jdbcTemplate, @Value("${APP_ENV:local}") String environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "ok", "env", environment);
    }
}
