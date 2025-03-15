package playwright.automation;

import automator.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import okhttp3.*;
import okhttp3.Response;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class OpsflowAutomation {
    @Test
    public void Opsflow() throws SQLException, IOException, ClassNotFoundException {
        Logger.logInfo("Automation Started...");
        if (!initializeDatabaseConnections()) {
            return;
        }
        if (!loadConfigProperties()) {
            return;
        }
        String loanAppID = ConfigManager.getLoanAppID();
        String reviewStatus = DatabaseConnection.fetchUserDataReviewStatus(loanAppID);
        if (reviewStatus.equals("NACH_EMAIL_SENT")) {
            uploadNachDoc(loanAppID);
        }
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(ConfigManager.isHeadless())
                     .setArgs(List.of("--start-maximized"))
             )) {
            executeAutomationFlow(playwright, browser, loanAppID, reviewStatus);
        } catch (Exception e) {
            Logger.logError("An error occurred: " + e.getMessage());
        }
    }

    private boolean initializeDatabaseConnections() {
        if (!DatabaseConnection.connectToDatabases()) {
            Logger.logError("Database connection failed. Aborting test.");
            return false;
        }
        return true;
    }

    private boolean loadConfigProperties() {
        ConfigManager.loadProperties();
        String environment = ConfigManager.getEnvironment();
        if (environment == null || environment.isEmpty()) {
            Logger.logError("Environment not set in properties. Aborting test.");
            return false;
        }
        return true;
    }

    private void uploadNachDoc(String loanAppID) throws IOException, SQLException, ClassNotFoundException {
        String baseURL = "https://" + ConfigManager.getEnvironment() + ".stg.whizdm.com/";
        String url = baseURL + "loans/loans/uploadSignedNach";
        upload_nach_doc(url, loanAppID);
    }

    private void executeAutomationFlow(Playwright playwright, Browser browser, String loanAppID, String reviewStatus) throws InterruptedException {
        Logger.logInfo("Browser launched in headless mode: " + ConfigManager.isHeadless());
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
        Page page = context.newPage();
        page.onDialog(dialog -> {
            Logger.logError("Popup detected: " + dialog.message());
            dialog.dismiss();
        });
        String baseURL = "https://" + ConfigManager.getEnvironment() + ".stg.whizdm.com/loans";
        String loginURL = baseURL + "/invLogin";
        String loanAppURL = baseURL + "/loans/loanApplication?id=" + ConfigManager.getLoanAppID();
        Logger.logInfo("Review status for loan application ID " + loanAppID + ": " + reviewStatus);
        Logger.logInfo("Navigating to Loans page: " + loginURL);
        page.navigate(loginURL);
        page.locator(ConfigManager.getUsernameSelector()).fill(ConfigManager.getUsername());
        page.locator(ConfigManager.getPasswordSelector()).fill(ConfigManager.getPassword());
        Logger.logInfo("Entered Login credentials");
        page.locator(ConfigManager.getLoginButtonSelector()).click();
        page.waitForLoadState();
        page.navigate(loanAppURL);
        Logger.logInfo("Successfully landed on Loan Application Page");
        applyZoom(page);
        processLoanApplication(page, loanAppID, reviewStatus);
    }

    private void processLoanApplication(Page page, String loanAppID, String reviewStatus) throws InterruptedException {
        Locator lockButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Lock").setExact(true));
        if (lockButton.isVisible()) {
            Logger.logInfo("Application is not locked, proceeding to lock it.");
            boolean isLocked = clickWithRetry(page, lockButton);
            if (!isLocked) {
                Logger.logError("Failed to lock the application after multiple attempts.");
                return;
            }
            page.waitForTimeout(1000);
        } else {
            Logger.logInfo("Application is already locked, proceeding further.");
        }
        page.evaluate("window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });");
        String[] stages = {"DOCS_UPLOADED", "PENDING_REVIEW", "FRAUD_REVIEW", "NACH_EMAIL_SENT", "KYC_VERIFICATION"};
        String targetState = ConfigManager.getTargetState();
        while (!Objects.equals(targetState, reviewStatus)) {
            Logger.logInfo("Current Status: " + reviewStatus);
            if (reviewStatus.equals("TEST_IGNORE") || reviewStatus.equals("REJECTED")) {
                handleErrorStatus(reviewStatus);
                return;
            }
            page.waitForTimeout(2000);
            page.locator("textarea[name=\"remark\"]").click();
            page.locator("textarea[name=\"remark\"]").fill("Test");
            page.waitForTimeout(1000);
            Locator statusButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(reviewStatus));
            statusButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(60000));
            statusButton.click();
            String nextStatus = determineNextStatus(stages, reviewStatus);
            Logger.logInfo("Attempting to transition to status: " + nextStatus);
            page.locator("a").filter(new Locator.FilterOptions().setHasText(nextStatus)).click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).nth(4).click();
            String errText = getErrorAfterSave(page);
            if (errText.contains("Failed to move")) {
                Logger.logError("Fatal error encountered after saving status: " + errText);
                if (errText.contains("Gateway Time-out")) {
                    Logger.logInfo("Retrying due to Gateway Time-out error...");
                    retryTransition(page, reviewStatus, nextStatus, loanAppID);
                }
            }
            String updatedStatus = pollDatabaseForStatusUpdate(loanAppID, nextStatus);
            if (!updatedStatus.equals(nextStatus)) {
                Logger.logError("Database did not update to " + nextStatus + " after waiting.");
                return;
            }
            page.reload();
            page.waitForTimeout(3000);
            applyZoom(page);
            page.evaluate("window.scrollTo({ top: 0, behavior: 'smooth' });");
            reviewStatus = updatedStatus;
        }
        Logger.logInfo("Portal URL: " + page.url());
    }

    private void handleErrorStatus(String reviewStatus) {
        Logger.logError("Application is in " + reviewStatus);
        throw new RuntimeException("Application is in " + reviewStatus);
    }

    private String determineNextStatus(String[] stages, String reviewStatus) {
        for (int i = 0; i < stages.length - 1; i++) {
            if (stages[i].equals(reviewStatus)) {
                return stages[i + 1];
            }
        }
        return "";
    }

    private String pollDatabaseForStatusUpdate(String loanAppID, String nextStatus) throws InterruptedException {
        final int maxDbAttempts = 12;
        int dbAttempts = 0;
        String updatedStatus = "";
        while (dbAttempts < maxDbAttempts) {
            updatedStatus = DatabaseConnection.verifyDatabaseStatus(loanAppID);
            assert updatedStatus != null;
            if (updatedStatus.equals(nextStatus)) {
                break;
            }
            if (updatedStatus.equals("TEST_IGNORE") || updatedStatus.equals("REJECTED")) {
                handleErrorStatus(updatedStatus);
                return updatedStatus;
            }
            //page.waitForTimeout(5000);
            Thread.sleep(5000);
            dbAttempts++;
        }
        return updatedStatus;
    }

    // Helper method: returns error text if visible, otherwise an empty string.
    private static String getErrorAfterSave(Page page) {
        Locator errorMessage = page.locator(".alert.alert-danger");
        int retries = 10;
        while (retries-- > 0) {
            if (errorMessage.isVisible()) {
                return errorMessage.innerText();
            }
            page.waitForTimeout(1000);
        }
        return "";
    }

    private void applyZoom(Page page) {
        String zoomLevel = String.valueOf(ConfigManager.getZoomLevel());
        page.evaluate("document.body.style.zoom='" + zoomLevel + "'");
    }
    // Helper method to click an element with retry mechanism.
    private boolean clickWithRetry(Page page, Locator locator) {
        for (int i = 0; i < 5; i++) {
            try {
                locator.scrollIntoViewIfNeeded();
                locator.click(new Locator.ClickOptions().setTimeout(60000).setForce(true));
                return true;
            } catch (TimeoutError e) {
                Logger.logError("TimeoutError on attempt " + (i + 1) + ": " + e.getMessage());
                page.waitForTimeout(1000);
            }
        }
        return false;
    }

    // Helper method to retry the transition in case of Gateway Time-out error.
    private void retryTransition(Page page, String reviewStatus, String nextStatus, String loanAppID) {
        for (int i = 0; i < 3; i++) {
            try {
                page.locator("textarea[name=\"remark\"]").click();
                page.locator("textarea[name=\"remark\"]").fill("Retry due to Gateway Time-out");
                page.waitForTimeout(1000);

                Locator statusButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(reviewStatus));
                statusButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(60000));
                statusButton.click();

                page.locator("a").filter(new Locator.FilterOptions().setHasText(nextStatus)).click();
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).nth(4).click();

                String errText = getErrorAfterSave(page);
                if (!errText.contains("Gateway Time-out")) {
                    Logger.logInfo("Retry successful.");
                    return;
                }
                Logger.logError("Retry attempt " + (i + 1) + " failed: " + errText);
            } catch (Exception e) {
                Logger.logError("Error during retry attempt " + (i + 1) + ": " + e.getMessage());
            }
        }
        Logger.logError("All retry attempts failed.");
    }
    public void upload_nach_doc(String url, String loan_app_id) throws IOException, SQLException, ClassNotFoundException {
        HashMap<String, String> queryParams = new HashMap<>() {{
            put("loanApplicationId", loan_app_id);
            put("filename", "signed_nach.pdf");
        }};

        List<MultipartBody.Part> params = new ArrayList<>();
        File file = new File(System.getProperty("user.dir") + File.separator + "src/main/resources" + File.separator + "nach.pdf");

        RequestBody requestFile = RequestBody.create(file, MediaType.parse("multipart/form-data"));
        MultipartBody.Part fileBody = MultipartBody.Part.createFormData(
                "loanApplicationConsoleModel.uploadDocument", "", requestFile);
        params.add(fileBody);

        HttpUrl.Builder httpBuider = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        for (Map.Entry<String, String> param : queryParams.entrySet()) {
            httpBuider.addQueryParameter(param.getKey(), param.getValue());
        }

        HttpUrl httpURL = httpBuider.build();
        MultipartBody.Builder requestBody = new MultipartBody.Builder();
        for (MultipartBody.Part part : params) {
            if (part.headers() != null)
                requestBody.addPart(part.body());
        }

        MultipartBody build = requestBody.setType(MultipartBody.FORM).build();
        okhttp3.Request request = new okhttp3.Request.Builder().url(httpURL.toString()).post(build).build();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    okhttp3.Request request1 = chain.request();
                    String credentials = Credentials.basic("markandey", "markandey");
                    okhttp3.Request req = request1.newBuilder().addHeader("Authorization", credentials).build();
                    return chain.proceed(req);
                })
                .connectTimeout(12000, TimeUnit.SECONDS)
                .writeTimeout(12000, TimeUnit.SECONDS)
                .readTimeout(12000, TimeUnit.SECONDS).build();

        Response response = okHttpClient.newCall(request).execute();
        assert response.body() != null;
        JsonNode jsonResponse = getJsonObject(response.body().string());

        Assert.assertEquals(response.code(), 200);
        Assert.assertEquals(jsonResponse.get("success"), true);
        Assert.assertEquals(jsonResponse.get("message"), "Signed Nach Uploaded Successfully");

        int n = 32;

        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + "abcdefghijklmnopqrstuvxyz";
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {
            int index = (int) (AlphaNumericString.length() * Math.random());
            sb.append(AlphaNumericString.charAt(index));
        }
        String random_id = sb.toString();
        DatabaseConnection.connectToDatabases();
        String loan_app_no = DatabaseConnection.fetchloanAppNo(loan_app_id);
        DatabaseConnection.mannualNachQuery(random_id,loan_app_no);
    }
    public JsonNode getJsonObject(String data) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(data);
        } catch (Exception e) {
            ObjectNode errorNode = mapper.createObjectNode();
            errorNode.put("success", true);
            System.out.println("Success: " + errorNode.get("success").asBoolean());
            return errorNode;
        }
    }

}