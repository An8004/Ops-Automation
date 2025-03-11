
package automator;
import com.mysql.cj.log.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class ServerStatusCheck {

    private static boolean serverCheckCompleted = false;

    public static void main(String[] args) {
        if (!serverCheckCompleted) {
            Properties properties = new Properties();
            try {
                FileInputStream input = new FileInputStream("src/main/resources/config.properties");
                properties.load(input);

                String environment = properties.getProperty("environment");
                //logger.info("Connecting to environment: " + environment);
                //Logger.logInfo(getTimestamp() + "Connecting to environment: " + environment);

                String lendingServerUrl = "https://" + environment + ".stg.whizdm.com" + properties.getProperty("LendingServer");
                String creditServerUrl = "https://" + environment + ".stg.whizdm.com" + properties.getProperty("CreditServer");
                String asgardServerUrl = "https://" + environment + ".stg.whizdm.com" + properties.getProperty("AsgardServer");

                checkServerStatus("Lending", lendingServerUrl, "Loans page");
                checkServerStatus("Credit", creditServerUrl, "Swagger");
                checkServerStatus("Asgard", asgardServerUrl, "SUCCESS message");

                serverCheckCompleted = true;

            } catch (IOException e) {
                Logger.logInfo("ERROR loading properties file: " + e.getMessage());
            }
        }
    }

    private static void checkServerStatus(String dbName, String serverUrl, String expectedMessage) {
        try {
            if (serverUrl != null && !serverUrl.isEmpty()) {
                HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();

                if (responseCode == 200) {
                    Logger.logInfo(" INFO: " + dbName + " Server is up! Expected: " + expectedMessage + ". Response: " + responseMessage);
                } else {
                    Logger.logInfo(" ERROR: " + dbName + " Server is down! Response code: " + responseCode + " (" + responseMessage + ")");
                }

            } else {
                Logger.logInfo(" ERROR: No URL found for " + dbName + " server.");
            }
        } catch (IOException e) {
            Logger.logInfo(" ERROR: " + dbName + " Server check failed. Error: " + e.getMessage());
        }
    }

    private static String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return "[" + sdf.format(new Date()) + "]";
    }

    public static boolean isServerCheckCompleted() {
        return serverCheckCompleted;
    }
}
