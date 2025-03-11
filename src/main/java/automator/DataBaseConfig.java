package automator;

import org.aeonbits.owner.Config;

@Config.Sources({"file:${user.dir}/src/main/resources/config.properties"})
public interface DataBaseConfig extends Config {

    /* environments */
    String lending_environment();
    String data_environment();
    String credit_environment();
    String electron_environment();
    String callinginfra_environment();
    String portaldb_environment();


    @Key("${environment}.lending")
    String lendingUrl();

    @Key("${environment}.lending_database_username")
    String lendingDBUserName();

    @Key("${environment}.lending_database_password")
    String lendingDBPassword();


    @Key("${data_environment}.data")
    String dataUrl();

    @Key("${data_environment}.data_database_username")
    String dataDBUser();

    @Key("${data_environment}.data_database_password")
    String dataDBPassword();


    @Key("${credit_environment}.credit")
    String creditUrl();

    @Key("${credit_environment}.credit_database_username")
    String creditDBUser();

    @Key("${credit_environment}.credit_database_password")
    String creditDBPassword();


    @Key("${electron_environment}.electron")
    String electronUrl();

    @Key("${electron_environment}.electron_database_username")
    String electronDBUser();

    @Key("${electron_environment}.electron_database_password")
    String electronDBPassword();


    @Key("${callinginfra_environment}.callinginfra")
    String callingInfraUrl();

    @Key("${callinginfra_environment}.callinginfra_database_username")
    String callingInfraUserName();
    @Key("${callinginfra_environment}.callinginfra_database_password")
    String callingInfraPassword();


    @Key("${portaldb_environment}.portaldb")
    String portaldbUrl();
    @Key("${portaldb_environment}.portaldb_database_username")
    String portaldbUserName();
    @Key("${portaldb_environment}.portaldb_database_password")
    String portaldbPassword();
}
