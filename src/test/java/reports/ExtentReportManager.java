
package reports;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ExtentReportManager {
    private static ExtentReports extent;
    private ExtentTest test;

    @BeforeClass
    public void setup() {
        extent = ExtentReportManager.getInstance();
        test = extent.createTest("Sample Test");
    }

    private static ExtentReports getInstance() {
        if (extent == null) {
            extent = new ExtentReports();
        }
        return extent;
    }

    @Test
    public void testPass() {
        test.log(Status.INFO, "Starting testPass");
        test.log(Status.PASS, "testPass passed");
    }

    @Test
    public void testFail() {
        test.log(Status.INFO, "Starting testFail");
        try {
            throw new Exception("Intentional failure");
        } catch (Exception e) {
            test.log(Status.FAIL, "testFail failed: " + e.getMessage());
        }
    }

    @Test
    public void testSkip() {
        test.log(Status.INFO, "Starting testSkip");
        test.log(Status.SKIP, "testSkip skipped");
    }

    @AfterClass
    public void tearDown() {
        extent.flush();
    }
}
