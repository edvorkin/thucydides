package net.thucydides.core.webdriver;

import net.thucydides.core.steps.StepEventBus;
import net.thucydides.core.webdriver.stubs.NavigationStub;
import net.thucydides.core.webdriver.stubs.OptionsStub;
import net.thucydides.core.webdriver.stubs.TargetLocatorStub;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotVisibleException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A proxy class for webdriver instances, designed to prevent the browser being opened unnecessarily.
 */
public class WebDriverFacade implements WebDriver, TakesScreenshot {

    private final Class<? extends WebDriver> driverClass;

    private final WebDriverFactory webDriverFactory;

    protected WebDriver proxiedWebDriver;

    private static final Logger LOGGER = LoggerFactory.getLogger(WebDriverFacade.class);

    public WebDriverFacade(final Class<? extends WebDriver> driverClass,
                           final WebDriverFactory webDriverFactory) {
        this.driverClass = driverClass;
        this.webDriverFactory = webDriverFactory;
    }

    public Class<? extends WebDriver>  getDriverClass() {
        return driverClass;
    }

    public WebDriver getProxiedDriver() {
        if (proxiedWebDriver == null) {
            proxiedWebDriver = newProxyDriver();
            WebdriverProxyFactory.getFactory().notifyListenersOfWebdriverCreationIn(this);
        }
        ensureValidDriver();
        return proxiedWebDriver;
    }

    public boolean isEnabled() {
        return !StepEventBus.getEventBus().webdriverCallsAreSuspended();
    }
    /**
     * Workaround for Webdriver issue 1438 (http://code.google.com/p/selenium/issues/detail?id=1438)
     */
    private void ensureValidDriver() {
        try {
            proxiedWebDriver.getCurrentUrl();
        } catch (WebDriverException e) {
            proxiedWebDriver.switchTo().defaultContent();
        }
    }

    public void reset() {
        if (proxiedWebDriver != null) {
            forcedQuit();
        }
        proxiedWebDriver = null;

    }

    private void forcedQuit() {
        try {
            getDriverInstance().quit();
            proxiedWebDriver = null;
        } catch (WebDriverException e) {
            LOGGER.warn("Closing a driver that was already closed",e);
        }
    }

    protected WebDriver newProxyDriver() {
        return newDriverInstance();
    }

    private WebDriver newDriverInstance() {
        try {
            return webDriverFactory.newWebdriverInstance(driverClass);
        } catch (Exception e) {
            LOGGER.error("FAILED TO CREATE NEW DRIVER INSTANCE " + driverClass, e);
            throw new UnsupportedDriverException("Could not instantiate " + driverClass, e);
        }
    }

    public <X> X getScreenshotAs(final OutputType<X> target) {
        if (proxyInstanciated() && driverCanTakeScreenshots()) {
            try {
                return ((TakesScreenshot) getProxiedDriver()).getScreenshotAs(target);
            } catch (WebDriverException e) {
                LOGGER.warn("Failed to take screenshot - driver closed already?", e);
            } catch (OutOfMemoryError outOfMemoryError) {
                // Out of memory errors can happen with extremely big screens, and currently Selenium does
                // not handle them correctly/at all.
                LOGGER.error("Failed to take screenshot - out of memory", outOfMemoryError);
            }
        }
        return null;
    }

    private boolean driverCanTakeScreenshots() {
        return (TakesScreenshot.class.isAssignableFrom(getProxiedDriver().getClass()));
    }

    public void get(final String url) {
        if (!isEnabled()) {
            return;
        }

        getProxiedDriver().get(url);
    }

    public String getCurrentUrl() {
        if (!isEnabled()) {
            return StringUtils.EMPTY;
        }

        return getProxiedDriver().getCurrentUrl();
    }

    public String getTitle() {
        if (!isEnabled()) {
            return StringUtils.EMPTY;
        }

        return getProxiedDriver().getTitle();
    }

    public List<WebElement> findElements(final By by) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        return getProxiedDriver().findElements(by);
    }

    public WebElement findElement(final By by) {
        if (!isEnabled()) {
            throw new ElementNotVisibleException("No element found for " + by.toString() + " (a previous step has failed)");
        }

        return getProxiedDriver().findElement(by);
    }

    public String getPageSource() {
        if (!isEnabled()) {
            return StringUtils.EMPTY;
        }

        return getProxiedDriver().getPageSource();
    }

    protected WebDriver getDriverInstance() {
        return proxiedWebDriver;
    }

    public void close() {
        if (proxyInstanciated()) {
            getDriverInstance().close();
        }
    }

    public void quit() {
        if (proxyInstanciated()) {
            try {
                getDriverInstance().quit();
            } catch (WebDriverException e) {
                LOGGER.warn("Error while quitting the driver - is this IE?", e);
            }
            proxiedWebDriver = null;
        }
    }

    protected boolean proxyInstanciated() {
        return (getDriverInstance() != null);
    }

    public Set<String> getWindowHandles() {
        if (!isEnabled()) {
            return new HashSet<String>();
        }

        return getProxiedDriver().getWindowHandles();
    }

    public String getWindowHandle() {
        if (!isEnabled()) {
            return StringUtils.EMPTY;
        }

        return getProxiedDriver().getWindowHandle();
    }

    public TargetLocator switchTo() {
        if (!isEnabled()) {
            return new TargetLocatorStub(this);
        }

        return getProxiedDriver().switchTo();
    }

    public Navigation navigate() {
        if (!isEnabled()) {
            return new NavigationStub();
        }

        return getProxiedDriver().navigate();
    }

    public Options manage() {
        if (!isEnabled()) {
            return new OptionsStub();
        }

        return getProxiedDriver().manage();
    }



    public boolean canTakeScreenshots() {
        return (driverClass != null) && (TakesScreenshot.class.isAssignableFrom(driverClass));
    }
}
