package com.stdevsec.atlas.system;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Key/value instance settings (organisation name, setup completion, defaults). */
@Component
public class InstanceSettings {

    public static final String SETUP_COMPLETED = "setup.completed";
    public static final String ORGANIZATION_NAME = "organization.name";
    public static final String DEFAULT_LOCALE = "default.locale";
    public static final String DEFAULT_TIME_ZONE = "default.timeZone";

    private final JdbcClient jdbc;

    InstanceSettings(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return jdbc.sql("select value from instance_setting where key = :key").param("key", key)
                .query(String.class).optional();
    }

    @Transactional
    public void put(String key, String value) {
        jdbc.sql("""
                insert into instance_setting(key, value, updated_at) values (:key, :value, now())
                on conflict (key) do update set value = excluded.value, updated_at = now()""")
                .param("key", key).param("value", value).update();
    }

    public boolean setupCompleted() {
        return get(SETUP_COMPLETED).map(Boolean::parseBoolean).orElse(false);
    }
}
