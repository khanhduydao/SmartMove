package com.smartmove.config;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;

/**
 * Centralized logger factory.
 * Replaces System.out/System.err with proper logging.
 */
public class LoggerFactory {

    private LoggerFactory() {
        throw new AssertionError("Utility class");
    }

    /**
     * Get logger for a class.
     */
    public static Logger getLogger(Class<?> clazz) {
        Logger logger = Logger.getLogger(clazz.getName());

        // Configure if not already configured
        if (logger.getHandlers().length == 0) {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.ALL);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
        }

        return logger;
    }

    /**
     * Get logger by name.
     */
    public static Logger getLogger(String name) {
        return Logger.getLogger(name);
    }
}