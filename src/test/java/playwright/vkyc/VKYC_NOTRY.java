package playwright.vkyc;

import automator.ConfigManager;
import automator.DatabaseConnection;
import automator.Logger;
import automator.Queries;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VKYC No Try Flow Automation Test
 * This class automates the VKYC No Try Flow process, performing database validation,
 * API calls, and browser automation using Playwright.
 * Validating loan application status.
 * Updating VKYC information in the Lending DB.
 * Checking and validating VKYC info.
 * Making an API call if conditions are met.
 * Verifying entries in calling_service_leads.
 * Verifying vendor lead details in the Calling DB.
 * @throws Exception if any validation or API call fails.
 * Implemented by: Anurag Singh
 */

public class VKYC_NOTRY{

    @Test
    public void testVkycNotryFlow() throws Exception {
        DatabaseConnection.connectToDatabases();
        Logger.logInfo("VKYC NOTRY FLOW STARTED...");
        String loanAppId = ConfigManager.getLoanAppID();
        assertNotNull(loanAppId, "loan_app_ID is missing in config.properties");
        Logger.logInfo("Loan App ID: " + loanAppId);
        String environment = ConfigManager.getEnvironment();
        assertNotNull(environment, "Environment is missing in config.properties");
        String apiUrl = "https://" + environment + ".stg.whizdm.com/loans/services/api/vkycCalling/createLeadVkycNoTry";
        Logger.logInfo("API URL: " + apiUrl);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime expectedTime = LocalDateTime.now().minusMinutes(60);
        String formattedDate = expectedTime.format(formatter);
        String loanAppNo;

        // **Step 1-7: Lending DB Operations**
        try (Connection lendingConn = establishConnection(DatabaseConnection.getLendingDBConnection(), "Lending DB")) {

            // Step 1: Verify application status
            if (!"REQ_CREDIT_CHECK".equals(validateApplicationStatus(lendingConn, loanAppId))) {
                throw new Exception("Invalid application status for loanAppId: " + loanAppId);
            }
            loanAppNo = getLoanAppNo(lendingConn, loanAppId);
            Logger.logInfo("Loan Application Number: " + loanAppNo);

            // Step 2: Update vkyc_info entry
            if (!updateVkycInfo(lendingConn, loanAppId, formattedDate)) {
                throw new Exception("Failed to update date_created in vkyc_info for loanAppId: " + loanAppId);
            }
            Logger.logInfo("Updated vkyc_info entry");

            // Step 3: Fetch and validate vkyc_info entry
            if (!validateVkycInfo(lendingConn, loanAppId)) {
                throw new Exception("vkyc_info validation failed for loanAppId: " + loanAppId);
            }
            Logger.logInfo("Validated vkyc_info entry");

            // Step 4: Check if entry exists in calling_service_leads (Lending DB)
            if (checkExistingEntryInCallingServiceLeads(lendingConn, loanAppId)) {
                return;
            }

            // Step 5: Hit API if conditions match
            hitVkycApi(apiUrl, loanAppId);

            // Step 6: Verify API response entry in calling_service_leads (Lending DB)
            if (!verifyCallingServiceLeadsEntry(lendingConn, loanAppId)) {
                throw new Exception("API did not create expected entry in calling_service_leads for loanAppId: " + loanAppId);
            }
        }

        // **Step 7: Vendor Lead Details Verification in Calling DB**
        try (Connection callingConn = establishConnection(DatabaseConnection.getCallingDBConnection(), "Calling DB")) {
            if (!verifyVendorLeadDetails(callingConn, loanAppNo)) {
                throw new Exception("vendor_lead_details validation failed for loanAppNo: " + loanAppNo);
            }
        }
        Logger.logInfo("VKYC NOTRY FLOW COMPLETED");
    }

    private Connection establishConnection(Connection conn, String dbName) throws Exception {
        assertNotNull(conn, "Failed to establish connection to " + dbName);
        return conn;
    }

    /**
     * Validates the application status by querying the database.
     *
     * @param conn      Database connection object.
     * @param loanAppId Loan application ID.
     * @return Application status as a string.
     * @throws Exception if any error occurs during the database query.
     */
    private String validateApplicationStatus(Connection conn, String loanAppId) throws Exception {
        if (isValidLoanAppId(loanAppId)) {
            throw new IllegalArgumentException("Invalid loan application ID format");
        }
        try (PreparedStatement stmt = conn.prepareStatement(Queries.REVIEW_STATUS_QUERY)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String status = rs.getString("user_data_review_status");
                Logger.logInfo("Application Status : " + status);
                return status;
            }
        }
        return null;
    }

    /**
     * Checks if the loan application ID is valid.
     *
     * @param loanAppId Loan application ID.
     * @return true if the ID is valid, false otherwise.
     */
    private boolean isValidLoanAppId(String loanAppId) {
        return loanAppId == null || !loanAppId.matches("[A-Za-z0-9_-]+");
    }

    /**
     * Retrieves the loan application number from the database.
     *
     * @param conn      Database connection object.
     * @param loanAppId Loan application ID.
     * @return Loan application number as a string.
     * @throws Exception if any error occurs during the database query.
     */
    private String getLoanAppNo(Connection conn, String loanAppId) throws Exception {
        if (isValidLoanAppId(loanAppId)) {
            throw new IllegalArgumentException("Invalid loan application ID format");
        }
        try (PreparedStatement stmt = conn.prepareStatement(Queries.LOAN_APP_NO)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("loan_application_no");
            }
        }
        return null;
    }

    /**
     * Updates VKYC information in the database.
     *
     * @param conn          Database connection object.
     * @param loanAppId     Loan application ID.
     * @param formattedDate Formatted date string.
     * @return true if the update was successful, false otherwise.
     * @throws Exception if any error occurs during the database update.
     */
    private boolean updateVkycInfo(Connection conn, String loanAppId, String formattedDate) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.UPDATE_VKYC_INFO_QUERY)) {
            stmt.setString(1, formattedDate);
            stmt.setString(2, loanAppId);
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

    /**
     * Validates VKYC information by querying the database.
     *
     * @param conn      Database connection object.
     * @param loanAppId Loan application ID.
     * @return true if the VKYC information is valid, false otherwise.
     * @throws Exception if any error occurs during the database query.
     */
    private boolean validateVkycInfo(Connection conn, String loanAppId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.FETCH_VKYC_INFO_QUERY)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String status = rs.getString("status");
                String provider = rs.getString("provider");
                String flowType = rs.getString("flow_type");
                int attempts = rs.getInt("attempts");

                Logger.logInfo(String.format("vkyc_info Data - status: %s, provider: %s, flow_type: %s, attempts: %d",
                        status, provider, flowType, attempts));

                return (status.equals("STARTED") || status.equals("INITIATED") || status.equals("VKYC_INVALIDATED"))
                        && ("IN_HOUSE".equals(provider) || "HYPERVERGE".equals(provider))
                        && "ASSISTED".equals(flowType)
                        && attempts == 0;
            }
        }
        return false;
    }

    /**
     * Checks for existing entries in the calling_service_leads table.
     *
     * @param conn      Database connection object.
     * @param loanAppId Loan application ID.
     * @return true if an entry exists, false otherwise.
     * @throws Exception if any error occurs during the database query.
     */
    private boolean checkExistingEntryInCallingServiceLeads(Connection conn, String loanAppId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.CHECK_CALLING_SERVICE_LEADS_QUERY)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String entityId = rs.getString("entity_id");
                Logger.logInfo(String.format("Entry already exists in calling_service_leads. entity_id: %s. Skipping execution.", entityId));
                return true;
            }
        }
        return false;
    }

    /**
     * Makes an API call to the VKYC service.
     *
     * @param apiUrl    API URL.
     * @param loanAppId Loan application ID.
     */
    private void hitVkycApi(String apiUrl, String loanAppId) {
        try (Playwright playwright = Playwright.create()) {
            APIRequestContext request = playwright.request().newContext();
            APIResponse response = request.get(apiUrl + "?loanAppId=" + loanAppId);
            Logger.logInfo("API Response Status Code: " + response.status());
            assertEquals(204, response.status(), "API request failed");

            if (response.status() != 204) {
                Logger.logError("Unexpected API response status: " + response.status());
                throw new RuntimeException("API request failed with status: " + response.status());
            }
        } catch (Exception e) {
            Logger.logError("Error during API call: " + e.getMessage());
            throw new RuntimeException("API call failed", e);
        }
    }

    /**
     * Verifies the entry in the calling_service_leads table.
     *
     * @param conn      Database connection object.
     * @param loanAppId Loan application ID.
     * @return true if the entry is verified, false otherwise.
     * @throws Exception if any error occurs during the database query.
     */
    private boolean verifyCallingServiceLeadsEntry(Connection conn, String loanAppId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.VERIFY_CALLING_SERVICE_LEADS_QUERY)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String entityId = rs.getString("entity_id");
                String campaignId = rs.getString("campaign_id");
                String status = rs.getString("status");

                Logger.logInfo(String.format("Entry Created in calling_service_leads - entity_id: %s, campaign_id: %s, status: %s",
                        entityId, campaignId, status));

                return "VKYC_NOTRY".equals(campaignId) && "ADDED".equals(status);
            }
        }
        return false;
    }

    /**
     * Verifies the vendor lead details by querying the database.
     *
     * @param conn      Database connection object.
     * @param loanAppNo Loan application number.
     * @return true if the vendor lead details are verified, false otherwise.
     * @throws Exception if any error occurs during the database query.
     */
    public boolean verifyVendorLeadDetails(Connection conn, String loanAppNo) throws Exception {
        if (loanAppNo == null || loanAppNo.isEmpty()) {
            Logger.logError("Invalid loanAppNo: " + loanAppNo);
            return false;
        }

        boolean isRecordPresent = false;
        int maxRetries = 20;
        int waitTime = 15000;

        for (int i = 0; i < maxRetries; i++) {
            try (PreparedStatement stmt = conn.prepareStatement(Queries.VERIFY_VENDOR_LEAD_DETAILS_QUERY)) {
                stmt.setString(1, loanAppNo);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String entityId = rs.getString("entity_id");
                    String campaignId = rs.getString("campaign_id");
                    String status = rs.getString("status");

                    Logger.logInfo(String.format("Entry Created in vendor_lead_details - entity_id: %s, campaign_id: %s, status: %s",
                            entityId, campaignId, status));

                    if ("READY_TO_ADD".equals(status)) {
                        if (hitPushCreatedLeadApi(entityId)) {
                            Logger.logInfo("pushCreatedLead API called successfully. Verifying status update...");
                            if (verifyUpdatedStatus(conn, entityId)) {
                                openCallingPortal();
                                return true;
                            } else {
                                Logger.logError("Status update verification failed.");
                            }
                        } else {
                            Logger.logError("pushCreatedLead API call failed.");
                        }
                    } else if ("ADDED".equals(status)) {
                        Logger.logInfo("Status is already ADDED. Opening Calling Portal...");
                        openCallingPortal();
                        return true;
                    }
                    isRecordPresent = true;
                }
            }
            if (i < maxRetries - 1) {
                Thread.sleep(waitTime);
            } else {
                Logger.logError("Entry not found or not READY_TO_ADD after " + maxRetries + " retries.");
            }
        }
        return isRecordPresent;
    }

    /**
     * Makes an API call to push created lead.
     *
     * @param entityId Entity ID.
     * @return true if the API call was successful, false otherwise.
     */
    private boolean hitPushCreatedLeadApi(String entityId) {
        String callingEnvironment = ConfigManager.getProperty("calling_environment");
        String apiUrl = "https://" + callingEnvironment + ".stg.whizdm.com/callingInfra/v1/cron/ameyo/pushCreatedLead?entityId=" + entityId;

        try (Playwright playwright = Playwright.create()) {
            APIRequestContext request = playwright.request().newContext();
            APIResponse response = request.get(apiUrl);

            int statusCode = response.status();
            Logger.logInfo("pushCreatedLead API Response Status Code: " + statusCode);

            if (statusCode != 204 && statusCode != 200) {
                Logger.logError("Unexpected API response status: " + statusCode);
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.logError("Error calling pushCreatedLead API: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the updated status in the vendor_lead_details table.
     * @param conn     Database connection object.
     * @param entityId Entity ID.
     * @return true if the status is updated to "ADDED", false otherwise.
     * @throws Exception if any error occurs during the database query.
     */
    private boolean verifyUpdatedStatus(Connection conn, String entityId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.VERIFY_VENDOR_LEAD_STATUS_QUERY)) {
            stmt.setString(1, entityId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String status = rs.getString("status");
                Logger.logInfo("Updated Status in vendor_lead_details: " + status);
                return "ADDED".equals(status);
            }
        }
        return false;
    }

    /**
     * Opens the calling portal in a browser.
     */
    private void openCallingPortal() {
        String portalUrl = "https://pwa-01-calling-portal-01.stg.whizdm.com/login";
        Logger.logInfo("Journey URL for Calling Portal: " + portalUrl);
        Logger.logInfo("Please login using the following credentials and continue the Calling Portal journey Manually.");
        Logger.logInfo("Username: navaneeths");
        Logger.logInfo("Password: navaneeths");
    }
}