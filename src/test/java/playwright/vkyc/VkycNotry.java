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

import static org.junit.jupiter.api.Assertions.*;

public class VkycNotry {

    @Test
    public void testVkycNotryFlow() throws Exception {
        DatabaseConnection.connectToDatabases();
        String loanAppId = ConfigManager.getLoanAppID();
        String entityId = ConfigManager.getEntityID();
        assertNotNull(loanAppId, "loan_app_ID is missing in config.properties");

        String environment = ConfigManager.getEnvironment();
        assertNotNull(environment, "Environment is missing in config.properties");

        String apiUrl = "https://" + environment + ".stg.whizdm.com/loans/services/api/vkycCalling/createLeadVkycNoTry";

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime expectedTime = LocalDateTime.now().minusMinutes(60);
        String formattedDate = expectedTime.format(formatter);

        // **Step 1-7: Lending DB Operations**
        try (Connection lendingConn = DatabaseConnection.getLendingDBConnection()) {
            assertNotNull(lendingConn, "Failed to establish connection to Lending DB");

            // Step 1: Verify application status
            assertEquals("REQ_CREDIT_CHECK", validateApplicationStatus(lendingConn, loanAppId), "Invalid application status");

            // Step 2: Update vkyc_info entry
            assertTrue(updateVkycInfo(lendingConn, loanAppId, formattedDate), "Failed to update date_created in vkyc_info");

            // Step 3: Fetch and validate vkyc_info entry
            assertTrue(validateVkycInfo(lendingConn, loanAppId), "vkyc_info validation failed");

            // Step 4: Check if entry exists in calling_service_leads (Lending DB)
            if (checkExistingEntryInCallingServiceLeads(lendingConn, loanAppId)) {
                return;
            }

            // Step 5: Hit API if conditions match
            hitVkycApi(apiUrl, loanAppId);

            // Step 6: Verify API response entry in calling_service_leads (Lending DB)
            assertTrue(verifyCallingServiceLeadsEntry(lendingConn, loanAppId,entityId), "API did not create expected entry in calling_service_leads");
        }

        // **Step 7: Vendor Lead Details Verification in Calling DB**
        try (Connection callingConn = DatabaseConnection.getCallingDBConnection()) {
            assertNotNull(callingConn, "Failed to establish connection to Calling DB");
            assertTrue(verifyVendorLeadDetails(callingConn, entityId), "vendor_lead_details validation failed");
        }
    }

    private String validateApplicationStatus(Connection conn, String loanAppId) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.REVIEW_STATUS_QUERY)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String status = rs.getString("user_data_review_status");
                Logger.logInfo("Application status for LoanAppID " + loanAppId + ": " + status);
                return status;
            }
        }
        return null;
    }

    private boolean updateVkycInfo(Connection conn, String loanAppId, String formattedDate) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.UPDATE_VKYC_INFO_QUERY)) {
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

                return (status.equals("STARTED") || status.equals("INITIATED") || status.equals("VKYC_INVALIDATED"))
                        && ("IN_HOUSE".equals(provider) || "HYPERVERGE".equals(provider))
                        && "ASSISTED".equals(flowType)
                        && attempts == 0;
            }
        }
        return false;
    }

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

    private void hitVkycApi(String apiUrl, String loanAppId) {
        try (Playwright playwright = Playwright.create()) {
            APIRequestContext request = playwright.request().newContext();
            APIResponse response = request.get(apiUrl + "?loanAppId=" + loanAppId);
            Logger.logInfo("API Response Status Code: " + response.status());
            assertEquals(204, response.status(), "API request failed");
        }
    }

    private boolean verifyCallingServiceLeadsEntry(Connection conn, String loanAppId,String entityId1) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(Queries.VERIFY_CALLING_SERVICE_LEADS_QUERY)) {
            stmt.setString(1, loanAppId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String entityId = rs.getString("entity_id");
                entityId1 = entityId;
                String campaignId = rs.getString("campaign_id");

                Logger.logInfo(String.format("Entry Created in calling_service_leads - entity_id: %s, campaign_id: %s", entityId, campaignId));
                return "VKYC_NOTRY".equals(campaignId);
            }
        }
        return false;
    }

    private boolean verifyVendorLeadDetails(Connection conn, String entityId1) throws Exception {
        boolean isRecordPresent = false;
        int retryCount = 0;
        int maxRetries = 10;  // Maximum retries
        int waitTime = 10000; // 5 seconds wait time

        while (retryCount < maxRetries) {
            try (PreparedStatement stmt = conn.prepareStatement(Queries.VERIFY_VENDOR_LEAD_DETAILS_QUERY)) {
                Logger.logInfo("Executing query: " + Queries.VERIFY_VENDOR_LEAD_DETAILS_QUERY + " with entityId = " + entityId1);
                stmt.setString(1, entityId1);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    String entityId = rs.getString("entity_id");
                    String campaignId = rs.getString("campaign_id");

                    Logger.logInfo(String.format("✅ Entry Found in vendor_lead_details - entity_id: %s, campaign_id: %s", entityId, campaignId));
                    isRecordPresent = (entityId != null && campaignId != null);
                    break;
                }
            }

            if (!isRecordPresent) {
                Logger.logError("[⚠️] Entry not found in vendor_lead_details. Retrying in " + (waitTime / 1000) + " seconds...");
                Thread.sleep(waitTime);
                retryCount++;
            }
        }

        return isRecordPresent;
    }
}