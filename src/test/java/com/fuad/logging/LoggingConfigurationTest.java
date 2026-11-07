package com.fuad.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {
    private LoggerContext context;

    @BeforeEach
    void loadProductionConfiguration() throws Exception {
        context = new LoggerContext();
        context.setName("logging-configuration-test");
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(Objects.requireNonNull(getClass().getResource("/logback.xml")));
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void shouldFormatCategoryParametersAndExceptionOnConsole() {
        ConsoleAppender<?> appender = assertInstanceOf(ConsoleAppender.class,
                context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE"));
        assertTrue(appender.isStarted());
        assertEquals("System.out", appender.getTarget());

        Logger logger = context.getLogger("com.fuad.tts.Worker");
        LoggingEvent event = new LoggingEvent(getClass().getName(), logger, Level.WARN,
                "Unable to stop {}: síntesis", new IllegalStateException("worker failed"),
                new Object[]{"Piper"});
        event.addMarker(LogCategory.TTS);
        PatternLayoutEncoder encoder = assertInstanceOf(PatternLayoutEncoder.class, appender.getEncoder());
        String formatted = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertTrue(formatted.contains("WARN  [TTS] com.fuad.tts.Worker - Unable to stop Piper: síntesis"));
        assertTrue(formatted.contains("java.lang.IllegalStateException: worker failed"));
        assertFalse(formatted.contains("%PARSER_ERROR"));
    }

    @Test
    void shouldApplyConfiguredApplicationAndLibraryLevels() {
        String appLevel = configuredLevel("ARES_LOG_LEVEL", "INFO");
        String rootLevel = configuredLevel("ARES_ROOT_LOG_LEVEL", "WARN");

        assertEquals(Level.toLevel(appLevel), context.getLogger("com.fuad.speech.Service").getEffectiveLevel());
        assertEquals(Level.toLevel(rootLevel), context.getLogger("external.Library").getEffectiveLevel());
    }

    private String configuredLevel(String name, String defaultValue) {
        return System.getProperty(name, Objects.requireNonNullElse(System.getenv(name), defaultValue));
    }
}
