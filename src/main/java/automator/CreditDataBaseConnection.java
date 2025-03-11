package automator;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CreditDataBaseConnection extends DataBase {
    private static CreditDataBaseConnection creditDataBaseConnection = null;

    private CreditDataBaseConnection() {}

    public static CreditDataBaseConnection getInstance() {
        if (creditDataBaseConnection == null) {
            creditDataBaseConnection = new CreditDataBaseConnection();
        }
        return creditDataBaseConnection;
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
            // Handle exception and log if necessary
            throw new SQLException("Error while setting connection to Credit database: " + e.getMessage(), e);
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
        if (statement != null && !statement.isClosed()) {
            statement.close();
        }

        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Override
    public boolean isConnectionOpen() throws SQLException {
        return super.isConnectionOpen();
    }
}
