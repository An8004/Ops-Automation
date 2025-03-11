package automator;

import java.sql.*;

public class DatabaseConnectionManager {
    private static DatabaseConnectionManager instance = null;
    private Connection connection;
    private Statement statement;

    private DatabaseConnectionManager() {}

    public static DatabaseConnectionManager getInstance() {
        if (instance == null) {
            instance = new DatabaseConnectionManager();
        }
        return instance;
    }

    public void setConnection(String databaseUrl, String user, String password) throws SQLException {
        try {
            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            connection = DriverManager.getConnection(databaseUrl, user, password);

            // Create statement
            statement = connection.createStatement();
        } catch (SQLException | ClassNotFoundException e) {
            throw new SQLException("Error while setting connection: " + e.getMessage(), e);
        }
    }

    public ResultSet getQueryResult(String query) throws SQLException {
        return statement.executeQuery(query);
    }

    public int updateQuery(String query) throws SQLException {
        return statement.executeUpdate(query);
    }

    public void closeConnection() throws SQLException {
        if (statement != null && !statement.isClosed()) {
            statement.close();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    public boolean isConnectionOpen() throws SQLException {
        return connection != null && !connection.isClosed();
    }
}
