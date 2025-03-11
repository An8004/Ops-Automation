package playwright.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.*;


@UsePlaywright(Demo.CustomOptions.class)
public class Demo {

    public static class CustomOptions implements OptionsFactory {
        @Override
        public Options getOptions() {
            return new Options().setHeadless(false);
        }
    }



    @Test
    void test(Page page) {
        page.navigate("https://ops-05.stg.whizdm.com/loans/loans/loanApplication?id=8a8385d095176c2101951795c6f601b1");
        page.locator("#j_username").click();
        page.locator("#j_username").fill("markandey");
        page.locator("#j_password").click();
        page.locator("#j_password").fill("markandey");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log In")).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("REQ_CREDIT_CHECK")).click();
        page.locator("a").filter(new Locator.FilterOptions().setHasText("USER_CANCELLED")).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Lock").setExact(true)).click();
        page.locator("textarea[name=\"remark\"]").click();
        page.locator("textarea[name=\"remark\"]").fill("Test");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).nth(4).click();
    }

}

