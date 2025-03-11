package automator;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class DataBase {
    public Connection connection;
    protected Statement statement;

    public abstract void setConnection(String databaseUrl, String user, String password) throws SQLException, ClassNotFoundException;

    public abstract ResultSet getQueryResult(String query) throws SQLException // read
    ;

    public abstract ResultSet updateQuery(String query) throws SQLException;

    public void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
            System.out.println(this.getClass().getSimpleName() + " Connection closed.");
        }
    }

    public boolean isConnectionOpen() throws SQLException {
        return connection != null && !connection.isClosed();
    }
}