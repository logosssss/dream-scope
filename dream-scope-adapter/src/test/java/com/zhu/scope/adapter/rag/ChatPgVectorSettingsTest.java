package com.zhu.scope.adapter.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatPgVectorSettingsTest {

    @Test
    void blankJdbcIsNotConfigured() {
        ChatPgVectorSettings settings = new ChatPgVectorSettings("  ", null, null, null, null);
        assertFalse(settings.configured());
        assertEquals("postgres", settings.username());
        assertEquals(ChatPgVectorSettings.DEFAULT_SCHEMA, settings.schema());
        assertEquals(ChatPgVectorSettings.DEFAULT_TABLE, settings.tableName());
    }

    @Test
    void jdbcUrlMarksConfigured() {
        ChatPgVectorSettings settings = new ChatPgVectorSettings(
                " jdbc:postgresql://127.0.0.1:5432/dream_scope ", "scope", "secret", "rag", "chunks");
        assertTrue(settings.configured());
        assertEquals("jdbc:postgresql://127.0.0.1:5432/dream_scope", settings.jdbcUrl());
        assertEquals("scope", settings.username());
        assertEquals("secret", settings.password());
        assertEquals("rag", settings.schema());
        assertEquals("chunks", settings.tableName());
    }

    @Test
    void parsesDatabaseNameAndMaintenanceUrl() {
        String jdbc = "jdbc:postgresql://47.119.115.162/dream_scope";
        assertEquals("dream_scope", ChatPgVectorSettings.databaseName(jdbc));
        assertEquals(
                "jdbc:postgresql://47.119.115.162/postgres",
                ChatPgVectorSettings.maintenanceJdbcUrl(jdbc));
        assertEquals(
                "jdbc:postgresql://127.0.0.1:5432/postgres?ssl=true",
                ChatPgVectorSettings.maintenanceJdbcUrl("jdbc:postgresql://127.0.0.1:5432/dream_scope?ssl=true"));
    }
}
