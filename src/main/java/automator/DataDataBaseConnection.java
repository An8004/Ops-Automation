package automator;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DataDataBaseConnection extends DataBase {
    private static DataDataBaseConnection dataDataBaseConnection = null;

    private DataDataBaseConnection() {}

    public static DataDataBaseConnection getInstance() {
        if (dataDataBaseConnection == null) {
            dataDataBaseConnection = new DataDataBaseConnection();
        }
        return dataDataBaseConnection;
    }

    @Override
    public void setConnection(String databaseUrl, String user, String password) throws SQLException {
        try {
            // Load MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            connection = DriverManager.getConnection(databaseUrl, user, password);

            // Create statement
            statement = connection.createStatement();
        } catch (SQLException | ClassNotFoundException e) {
            // Handle exception and throw with a more descriptive message
            throw new SQLException("Error while setting connection to Data database: " + e.getMessage(), e);
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
        // Close statement if it is open
        if (statement != null && !statement.isClosed()) {
            statement.close();
        }

        // Close connection if it is open
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Override
    public boolean isConnectionOpen() throws SQLException {
        return super.isConnectionOpen();
    }
}
