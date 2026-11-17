package com.champ.autobuild.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class BuilderLog {
    public static final Logger LOGGER = LoggerFactory.getLogger("champ-autobuild");

    private BuilderLog() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }
}
