package automator;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public class DatabaseConnection {

    private static Connection connection;
    // A static Properties object to hold config details (loaded once)
    private static Properties dbProps;

    // This method connects to the necessary databases based on the properties
    public static boolean connectToDatabases() {
        dbProps = new Properties();
        try {
            // Load the properties file once and keep it in dbProps
            FileInputStream input = new FileInputStream("src/main/resources/config.properties");
            dbProps.load(input);

            // Fetch environment from the properties file
            String environment = dbProps.getProperty("environment");

            Logger.logInfo("Connecting to environment: " + environment);

            // Determine the correct database URLs based on the environment
            String lendingDbUrl = dbProps.getProperty(environment + ".lending");
            String creditDbUrl = dbProps.getProperty(environment + ".credit");
            String electronDbUrl = dbProps.getProperty(environment + ".electron");
            String dataDbUrl = dbProps.getProperty(environment + ".data");
            String portalDbUrl = dbProps.getProperty(environment + ".portal");

            // Connect to each database (Lending, Credit, Electron, Data, Portal)
            boolean lendingConnection = connectToDatabase("Lending", LendingDataBaseConnection.getInstance(), lendingDbUrl,
                    dbProps.getProperty(environment + ".lending_database_username"),
                    dbProps.getProperty(environment + ".lending_database_password"));
            boolean creditConnection = connectToDatabase("Credit", CreditDataBaseConnection.getInstance(), creditDbUrl,
                    dbProps.getProperty(environment + ".credit_database_username"),
                    dbProps.getProperty(environment + ".credit_database_password"));
            boolean electronConnection = connectToDatabase("Electron", ElectronDataBaseConnection.getInstance(), electronDbUrl,
                    dbProps.getProperty(environment + ".electron_database_username"),
                    dbProps.getProperty(environment + ".electron_database_password"));
            boolean dataConnection = connectToDatabase("Data", DataDataBaseConnection.getInstance(), dataDbUrl,
                    dbProps.getProperty(environment + ".data_database_username"),
                    dbProps.getProperty(environment + ".data_database_password"));
            boolean portalConnection = connectToDatabase("Portal", PortalDataBaseConnection.getInstance(), portalDbUrl,
                    dbProps.getProperty(environment + ".portal_database_username"),
                    dbProps.getProperty(environment + ".portal_database_password"));

            // Optionally, connect to CallingInfra database if configured.
            String callingEnvironment = dbProps.getProperty("calling_environment");
            if (callingEnvironment != null && !callingEnvironment.isEmpty()) {
                String callingInfraDbUrl = dbProps.getProperty(callingEnvironment + ".callinginfra");
                if (callingInfraDbUrl != null) {
                    boolean callingInfraConnection = connectToDatabase(
                            "CallingInfra", CallingInfraDataBaseConnection.getInstance(),
                            callingInfraDbUrl,
                            dbProps.getProperty(callingEnvironment + ".callinginfra_database_username"),
                            dbProps.getProperty(callingEnvironment + ".callinginfra_database_password")
                    );
                    if (callingInfraConnection) {
                        Logger.logInfo("CallingInfra database connected successfully for environment: " + callingEnvironment);
                    } else {
                        Logger.logInfo("Error connecting to CallingInfra database for environment: " + callingEnvironment);
                    }
                } else {
                    Logger.logInfo("No callinginfra database configured for environment: " + callingEnvironment);
                }
            } else {
                Logger.logInfo("No calling_environment set in properties.");
            }

            return lendingConnection && creditConnection && electronConnection && dataConnection && portalConnection;

        } catch (IOException | SQLException | ClassNotFoundException e) {
            Logger.logInfo("Error during database connection: " + e.getMessage());
            return false;
        }
    }

    // This method is used in your OpsflowAutomation to fetch the review status
    public static String fetchUserDataReviewStatus(String loanAppID) {
        String query = "SELECT user_data_review_status FROM loan_application WHERE id = ?";
        String reviewStatus = null;

        try (Connection conn = LendingDataBaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, loanAppID);
            Logger.logInfo("Executing query: " + query + " with loanAppID = " + loanAppID);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    reviewStatus = rs.getString("user_data_review_status");
                    Logger.logInfo("Fetched review status: " + reviewStatus);
                } else {
                    Logger.logError("No data found for loan application ID: " + loanAppID);
                }
            }

        } catch (SQLException e) {
            Logger.logError("Error fetching user data review status: " + e.getMessage());
        }

        return reviewStatus;
    }

    // New helper method: polls the DB using config properties.
    public static String verifyDatabaseStatus(String applicationId) {
        // Ensure properties are loaded (they should be loaded in connectToDatabases)
        if (dbProps == null) {
            dbProps = new Properties();
            try {
                FileInputStream input = new FileInputStream("src/main/resources/config.properties");
                dbProps.load(input);
            } catch (IOException e) {
                Logger.logError("Error loading config.properties: " + e.getMessage());
                return null;
            }
        }
        String environment = dbProps.getProperty("environment");
        String url = dbProps.getProperty(environment + ".lending");
        String username = dbProps.getProperty(environment + ".lending_database_username");
        String password = dbProps.getProperty(environment + ".lending_database_password");

        String reviewStatus = null;
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement stmt = conn.prepareStatement("SELECT user_data_review_status FROM loan_application WHERE id = ?")) {
            stmt.setString(1, applicationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    reviewStatus = rs.getString("user_data_review_status");
                } else {
                    Logger.logError("No record found for application ID: " + applicationId);
                }
            }
        } catch (SQLException e) {
            Logger.logError("Database error: " + e.getMessage());
        }
        return reviewStatus;
    }

    // Other methods (executeQuery, executeUpdate, isConnectionValid, etc.) remain unchanged

    // Helper function to connect and print success message
    private static boolean connectToDatabase(String dbName, DataBase database, String url, String username, String password)
            throws SQLException, ClassNotFoundException {
        try {
            if (url != null && !url.isEmpty()) {
                database.setConnection(url, username, password);
                if (!dbName.equals("CallingInfra")) {
                    Logger.logInfo(dbName + " database Connected Successfully");
                }
                return true;
            } else {
                Logger.logInfo("No database URL found for " + dbName);
                return false;
            }
        } catch (SQLException | ClassNotFoundException e) {
            Logger.logInfo("Error connecting to " + dbName + " database: " + e.getMessage());
            return false;
        }
    }

    // This method will execute the SQL query and return the ResultSet
    public static ResultSet executeQuery(String query) {
        try {
            if (connection == null || connection.isClosed()) {
                Logger.logError("Database connection is not established.");
                return null;
            }
            Statement stmt = connection.createStatement();
            return stmt.executeQuery(query);
        } catch (SQLException e) {
            Logger.logError("Error executing query: " + e.getMessage());
            return null;
        }
    }

    // This method will execute an update query (for INSERT, UPDATE, DELETE)
    public static int executeUpdate(String query) {
        try {
            if (connection == null || connection.isClosed()) {
                Logger.logError("Database connection is not established.");
                return -1;
            }
            Statement stmt = connection.createStatement();
            return stmt.executeUpdate(query);
        } catch (SQLException e) {
            Logger.logError("Error executing update query: " + e.getMessage());
            return -1;
        }
    }

    public static boolean isConnectionValid() {
        try {
            String testQuery = "SELECT 1";
            ResultSet resultSet = executeQuery(testQuery);
            return resultSet != null;
        } catch (Exception e) {
            Logger.logError("Database connection check failed: " + e.getMessage());
            return false;
        }

    }
    public static String fetchloanAppNo(String loanAppID) throws SQLException, ClassNotFoundException {
        String environment = dbProps.getProperty("environment");
        String lendingDbUrl = dbProps.getProperty(environment + ".lending");
        connectToDatabase("Lending", LendingDataBaseConnection.getInstance(), lendingDbUrl,
                dbProps.getProperty(environment + ".lending_database_username"),
                dbProps.getProperty(environment + ".lending_database_password"));
        String query = "select * from loan_application where id = ?";
        String loan_app_no = null;
        try (Connection conn = LendingDataBaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)){

        stmt.setString(1, loanAppID);
        Logger.logInfo("Executing query: " + query + " with loanAppID = " + loanAppID);

        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                loan_app_no = rs.getString("loan_application_no");
                Logger.logInfo("Fetched loan app no: " + loan_app_no);
            } else {
                Logger.logError("No data found for loan application ID: " + loanAppID);
            }
        }
        } catch (SQLException e) {
            Logger.logError("Error executing" + e.getMessage());
        }
    return loan_app_no;

    }
    public static void nachDocQueueEntry(String loanAppNo) throws SQLException, ClassNotFoundException {
        String environment = dbProps.getProperty("environment");
        String lendingDbUrl = dbProps.getProperty(environment + ".lending");
        connectToDatabase("Lending", LendingDataBaseConnection.getInstance(), lendingDbUrl,
                dbProps.getProperty(environment + ".lending_database_username"),
                dbProps.getProperty(environment + ".lending_database_password"));
        String query = "select * from nach_document_queue where loan_application_no = ?";
        try (Connection conn = LendingDataBaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, loanAppNo);
            stmt.executeQuery();
        } catch (SQLException e) {
            Logger.logError("Error executing" + e.getMessage());
        }
    }
    public static void mannualNachQuery(String random_id,String loanAppNo) throws SQLException, ClassNotFoundException {
        String environment = dbProps.getProperty("environment");
        String lendingDbUrl = dbProps.getProperty(environment + ".lending");
        connectToDatabase("Lending", LendingDataBaseConnection.getInstance(), lendingDbUrl,
                dbProps.getProperty(environment + ".lending_database_username"),
                dbProps.getProperty(environment + ".lending_database_password"));
        String query = "INSERT INTO nach_document_queue VALUES (?, ?, 'Nach.pdf', " +
                "'PROCESSED', 'API', 'mv.atlas', '2020-02-01 21:38:00', '2021-02-08 22:59:36', NULL, 'APPROVED', 0, '', '', 0, NULL)" ;
        try (Connection conn = LendingDataBaseConnection.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, random_id);
            stmt.setString(2,loanAppNo);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Logger.logError("Error executing" + e.getMessage());
        }
    }
        public static Connection getConnection() {
        return connection;
    }

    public static Connection getLendingDBConnection() {
        return LendingDataBaseConnection.getInstance().getConnection();

    }

    public static void setConnection(Connection connection) {
        DatabaseConnection.connection = connection;
    }

    public static Connection getCallingDBConnection() {
        return CallingInfraDataBaseConnection.getInstance().getConnection();
        //return CallingDataBaseConnection.getInstance().getConnection();

    }
}