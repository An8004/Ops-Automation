package automator;


import java.sql.SQLException;

public class DBFactory {

    public enum CONNECTION_NAMES {
        LENDING, CREDIT, ELECTRON,DATA,CALLING_INFRA,PORTALDB
    }

    public DataBase getInstance(CONNECTION_NAMES connectionNames, String database_url,
                                String user_name, String password) throws SQLException, ClassNotFoundException {
        switch (connectionNames) {

            case LENDING: {
                DataBase lending = LendingDataBaseConnection.getInstance();
                lending.setConnection(database_url, user_name, password);
                return lending;
            }

            case CREDIT: {
                DataBase credit = CreditDataBaseConnection.getInstance();
                credit.setConnection(database_url, user_name, password);
                return credit;
            }



            case DATA: {
                DataBase data = DataDataBaseConnection.getInstance();
                data.setConnection(database_url, user_name, password);
                return data;
            }

            case ELECTRON: {
                DataBase electron = ElectronDataBaseConnection.getInstance();
                electron.setConnection(database_url, user_name, password);
                return electron;
            }

            case CALLING_INFRA: {
                DataBase calling_infra = CallingInfraDataBaseConnection.getInstance();
                calling_infra.setConnection(
                        database_url, user_name, password
                );
                return calling_infra;
            }

            case PORTALDB: {
                DataBase portaldb = PortalDataBaseConnection.getInstance();
                portaldb.setConnection(
                        database_url, user_name, password
                );
                return portaldb;
            }
            default: {
                return null;
            }
        }
    }

}
