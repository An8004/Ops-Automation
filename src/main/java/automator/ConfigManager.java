package automator;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigManager {
    private static final Properties properties = new Properties();

    // Static Block to Load Properties Once
    static {
        try (FileInputStream file = new FileInputStream("src/main/resources/config.properties")) {
            properties.load(file);
        } catch (IOException e) {
            System.err.println("❌ Error loading config.properties: " + e.getMessage());
        }
    }

    // Generic getProperty method
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    // Getter Methods for Application Configuration
    public static String getEnvironment() {
        return properties.getProperty("environment");
    }

    public static String getLoanAppID() {
        return properties.getProperty("loan_app_ID");
    }
    public static String getTargetState() {
        return properties.getProperty("TARGET_STATE");
    }

    public static boolean isHeadless() {
        return Boolean.parseBoolean(properties.getProperty("headless"));
    }

    public static String getUsername() {
        return properties.getProperty("username");
    }

    public static String getPassword() {
        return properties.getProperty("password");
    }

    public static String getUsernameSelector() {
        return properties.getProperty("username_selector");
    }

    public static String getPasswordSelector() {
        return properties.getProperty("password_selector");
    }

    public static String getLoginButtonSelector() {
        return properties.getProperty("login_button_selector");
    }

    public static double getZoomLevel() {
        return Double.parseDouble(properties.getProperty("zoom_level", "0.5"));
    }

    // Getter Methods for Database Configuration based on the selected environment
    public static String getDatabaseUrl() {
        String environment = getEnvironment();
        return properties.getProperty(environment + ".lending");  // Dynamically using the environment value
    }

    public static String getDatabaseUsername() {
        String environment = getEnvironment();
        return properties.getProperty(environment + ".lending_database_username");  // Dynamically using the environment value
    }

    public static String getDatabasePassword() {
        String environment = getEnvironment();
        return properties.getProperty(environment + ".lending_database_password");  // Dynamically using the environment value
    }
    public static String getUserDataReviewStatus() {
        return properties.getProperty("user_data_review_status", "default_status");
    }

    public static void loadProperties() {
    }
}
