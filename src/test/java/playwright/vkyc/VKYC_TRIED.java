package playwright.vkyc;

import automator.ConfigManager;
import automator.DatabaseConnection;
import automator.Logger;
import automator.Queries;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
/**
 * This class implements an automated test for the VKYC_TRIED flow, verifying database updates
 * and API interactions related to the VKYC process.
 */
public class VKYC_TRIED {
    /**
     * Tests the VKYC Tried Flow by verifying database entries and API calls.
     * @throws Exception if any validation or execution step fails.
     */
    @Test
    public void testVkyctriedFlow() throws Exception {
        DatabaseConnection.connectToDatabases();
        Logger.logInfo("VKYC TRIED FLOW STARTED...");
        String loanAppId = ConfigManager.getLoanAppID();
        assertNotNull(loanAppId, "loan_app_ID is missing in config.properties");
        Logger.logInfo("Loan App ID: " + loanAppId);
        String environment = ConfigManager.getEnvironment();
        assertNotNull(environment, "Environment is missing in config.properties");
        String apiUrl = "https://" + environment + ".stg.whizdm.com/loans/services/api/vkycCalling/createLeadVkycTried";
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
                throw new Exception("Failed to update date_modified in vkyc_info for loanAppId: " + loanAppId);
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
        Logger.logInfo("VKYC TRIED FLOW COMPLETED");
    }

    private Connection establishConnection(Connection conn, String dbName) throws Exception {
        assertNotNull(conn, "Failed to establish connection to " + dbName);
        return conn;
    }
    /**
     * Validates the application status in the Lending database.
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

    private boolean isValidLoanAppId(String loanAppId) {
        return loanAppId == null || !loanAppId.matches("[A-Za-z0-9_-]+");
    }

    /**
     * Fetches the loan application number for a given loan application ID.
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
     * Updates the vkyc_info entry in the Lending database.
     */
    private boolean updateVkycInfo(Connection conn, String loanAppId, String formattedDate) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.UPDATE_VKYC_INFO_RETRYQUERY)) {
            stmt.setString(1, formattedDate);
            stmt.setString(2, loanAppId);
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        }
    }

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

                return (status.equals("IN_PROGRESS") || status.equals("INITIATED") || status.equals("FAILED") ||
                        status.equals("RETRY") || status.equals("VKYC_INVALIDATED"))
                        && "ASSISTED".equals(flowType)
                        && attempts > 0;
            }
        }
        return false;
    }

    private boolean checkExistingEntryInCallingServiceLeads(Connection conn, String loanAppId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.CHECK_CALLING_SERVICE_LEADS_TRIED_QUERY)) {
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
     * Verifies the entry in calling_service_leads after API execution.
     */
    private boolean verifyCallingServiceLeadsEntry(Connection conn, String loanAppId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.VERIFY_CALLING_SERVICE_LEADS_TRIED_QUERY)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String entityId = rs.getString("entity_id");
                String campaignId = rs.getString("campaign_id");
                String status = rs.getString("status");

                Logger.logInfo(String.format("Entry Created in calling_service_leads - entity_id: %s, campaign_id: %s, status: %s",
                        entityId, campaignId, status));

                return "VKYC_TRIED".equals(campaignId) && "ADDED".equals(status);
            }
        }
        return false;
    }
    /**
     * Verifies the vendor lead details in the database.
     *
     * @param conn       The database connection object.
     * @param loanAppNo  The loan application number.
     * @return           True if the record is found and processed successfully, false otherwise.
     * @throws Exception If a database error occurs.
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
     * Calls the pushCreatedLead API for the given entity ID.
     *
     * @param entityId The entity ID to push.
     * @return         True if the API call is successful, false otherwise.
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
     * Verifies if the status of the vendor lead has been updated.
     *
     * @param conn     The database connection object.
     * @param entityId The entity ID to verify.
     * @return         True if the status is updated to "ADDED", false otherwise.
     * @throws Exception If a database error occurs.
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
     * Opens the Calling Portal for further processing.
     */
    private void openCallingPortal() {
        String portalUrl = "https://pwa-01-calling-portal-01.stg.whizdm.com/login";
        Logger.logInfo("Journey URL for Calling Portal: " + portalUrl);
        Logger.logInfo("Please login using the following credentials and continue the Calling Portal journey Manually.");
        Logger.logInfo("Username: navaneeths");
        Logger.logInfo("Password: navaneeths");
    }
}