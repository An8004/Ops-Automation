package playwright.automation;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import automator.DatabaseConnection;
import automator.Logger;
import automator.ServerStatusCheck;
import com.microsoft.playwright.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;

public class CancelExpiredTopupTest {

    private Connection lendingConnection;
    private APIRequestContext requestContext;

    @BeforeClass
    public void setUp() throws Exception {
        Logger.logInfo("Starting setup...");

        // Connect to databases
        DatabaseConnection.connectToDatabases();

        // Perform server status check if not completed
        if (!ServerStatusCheck.isServerCheckCompleted()) {
            Logger.logInfo("Running server status check...");
            ServerStatusCheck.main(null);
            Assert.assertTrue(ServerStatusCheck.isServerCheckCompleted(),
                    getTimestamp() + " ERROR: Server status check failed!");
        }

        // Load properties from configuration file
        Properties properties = loadProperties();

        String environment = properties.getProperty("environment");
        String url = properties.getProperty(environment + ".lending");
        String username = properties.getProperty(environment + ".lending_database_username");
        String password = properties.getProperty(environment + ".lending_database_password");

        // Establish a connection to the lending database
        lendingConnection = DriverManager.getConnection(url, username, password);

        // Initialize Playwright and set up API request context
        Playwright playwright = Playwright.create();
        requestContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL("https://" + environment + ".stg.whizdm.com"));
    }

    @Test
    public void testCancelExpiredTopup() throws Exception {
        Logger.logInfo("Fetching statuses before cron...");
        List<String> initialStatuses = getStatusesBeforeCron();

        Logger.logInfo("Triggering the API call to cancel expired top-ups...");
        APIResponse response = requestContext.get("/loans/services/api/topup/cron/cancelExpiredTopup");

        // Check the response status code and log accordingly
        Logger.logInfo("API Response Status Code: " + response.status());
        if (response.status() == 204) {
            Logger.logInfo("API call successful with 204 No Content.");
        } else if (response.status() == 200) {
            Logger.logInfo("API call successful with 200 OK.");
        } else {
            Assert.fail(getTimestamp() + " ERROR: API call failed! Expected: 200 or 204, Actual: " + response.status());
        }

        // Fetch updated statuses after the cron job execution
        Logger.logInfo("Fetching updated statuses after cron...");
        List<String> updatedStatuses = getStatusesAfterCron();

        // Validate that the application statuses were correctly updated
        validateStatuses(initialStatuses, updatedStatuses);

        // Fetch and log the count of newly cancelled applications
        int newlyCancelledCount = getNewlyCancelledCountAfterCron();
        Logger.logInfo("Newly system_cancelled app records count: " + newlyCancelledCount);
    }

    private List<String> getStatusesBeforeCron() throws Exception {
        String query = "SELECT user_data_review_status FROM loan_application " +
                "WHERE app_name = 'com.whizdm.moneyview.loans.topup' " +
                "AND user_data_review_status IN ('DOCS_UPLOADED', 'PENDING_REVIEW', 'FRAUD_REVIEW', 'NACH_EMAIL_SENT', 'KYC_VERIFICATION', 'MV_COMPLIANCE', 'LOAN_APPROVED');";
        return getStatusesFromDatabase(query);
    }

    private List<String> getStatusesAfterCron() throws Exception {
        String query = "SELECT user_data_review_status FROM loan_application " +
                "WHERE app_name = 'com.whizdm.moneyview.loans.topup' " +
                "AND user_data_review_status = 'SYSTEM_CANCELLED';";
        return getStatusesFromDatabase(query);
    }

    private List<String> getStatusesFromDatabase(String query) throws Exception {
        List<String> statuses = new ArrayList<>();
        try (PreparedStatement preparedStatement = lendingConnection.prepareStatement(query);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                statuses.add(resultSet.getString("user_data_review_status"));
            }
        }
        return statuses;
    }

    private void validateStatuses(List<String> initialStatuses, List<String> updatedStatuses) {
        for (String status : initialStatuses) {
            if (status.equals("READY_FOR_DISBURSAL") || status.equals("DISBURSAL_FAILED") ||
                    status.equals("DISBURSAL_INITIATED") || status.equals("DISBURSED")) {
                Assert.assertFalse(updatedStatuses.contains(status),
                        getTimestamp() + " ERROR: Invalid cancellation for status: " + status);
            } else {
                Assert.assertTrue(updatedStatuses.contains("SYSTEM_CANCELLED"),
                        getTimestamp() + " INFO: Status not cancelled: " + status);
            }
        }
    }

    private int getNewlyCancelledCountAfterCron() throws Exception {
        String query = "SELECT COUNT(*) FROM loan_application " +
                "WHERE app_name = 'com.whizdm.moneyview.loans.topup' " +
                "AND user_data_review_status = 'SYSTEM_CANCELLED' " +
                "AND DATE(application_status_change_date) = CURDATE() " +
                "AND application_status_change_date > (SELECT MAX(application_status_change_date) " +
                "FROM loan_application WHERE user_data_review_status = 'SYSTEM_CANCELLED' " +
                "AND app_name = 'com.whizdm.moneyview.loans.topup' " +
                "AND application_status_change_date < NOW())";  // Exclude already cancelled records

        try (Statement statement = lendingConnection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String getTimestamp() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        return "[" + dtf.format(LocalDateTime.now()) + "]";
    }

    private Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("src/main/resources/config.properties")) {
            properties.load(fis);
        }
        return properties;
    }
}