package automator;

import java.sql.*;

public class PortalDataBaseConnection extends DataBase {

    private static PortalDataBaseConnection portalDataBaseConnection = null;
    private static Connection connection;
    private static Statement statement;

    // Private constructor
    private PortalDataBaseConnection() {}

    // Singleton instance
    public static PortalDataBaseConnection getInstance() {
        if (portalDataBaseConnection == null) {
            portalDataBaseConnection = new PortalDataBaseConnection();
        }
        return portalDataBaseConnection;
    }

    @Override
    public void setConnection(String databaseUrl, String user, String password) throws SQLException {
        try {
            // Load JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            connection = DriverManager.getConnection(databaseUrl, user, password);

            // Create statement
            statement = connection.createStatement();
        } catch (SQLException | ClassNotFoundException e) {
            // Handle exception and throw with more descriptive message
            throw new SQLException("Error while setting connection to Portal database: " + e.getMessage(), e);
        }
    }
    @Override
    public ResultSet getQueryResult(String query) throws SQLException {
        return statement.executeQuery(query);
    } // read

    @Override
    public ResultSet updateQuery(String query) throws SQLException {
        try {
            statement.executeUpdate(query);
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public void closeConnection() throws SQLException {
        // Close statement if open
        if (statement != null && !statement.isClosed()) {
            statement.close();
        }

        // Close connection if open
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Override
    public boolean isConnectionOpen() throws SQLException {
        return connection != null && !connection.isClosed();
    }
}
