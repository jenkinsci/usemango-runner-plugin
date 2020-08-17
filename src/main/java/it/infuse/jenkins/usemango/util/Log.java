package it.infuse.jenkins.usemango.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Log {

    private static final Logger logger = Logger.getLogger("useMangoRunner");

    public static void info(String message) {
        logger.log(Level.INFO, message);
    }

    public static void fine(String message) {
        logger.log(Level.FINE, message);
    }

    public static void severe(String message){
        logger.log(Level.SEVERE, message);
    }
}
