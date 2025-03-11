package automator;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    // ANSI color codes
    private static final String GREEN = "\u001B[32m";  // Green for Success (INFO)
    private static final String RED = "\u001B[31m";    // Red for Errors
    private static final String RESET = "\u001B[0m";   // Reset to Default

    public static void logInfo(String message) {
        System.out.println(GREEN + "[" + getCurrentTimestamp() + "] INFO: " + message + RESET);
    }

    public static void logError(String message) {
        System.err.println(RED + "[" + getCurrentTimestamp() + "] ERROR: " + message + RESET);
    }

    private static String getCurrentTimestamp() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date());
    }
}
