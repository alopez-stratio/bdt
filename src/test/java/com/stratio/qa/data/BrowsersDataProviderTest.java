/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.qa.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Drives the browser matrix against a stand-in for the Selenium Grid console page, which is the
 * only thing {@code gridBrowsers} ever reads. The data providers take an ITestContext and a
 * Constructor they never use, so null is fine for both.
 */
public class BrowsersDataProviderTest {

    private MockWebServer grid;

    @BeforeMethod
    public void setUp() throws IOException {
        grid = new MockWebServer();
        grid.start();
        System.clearProperty("SELENIUM_GRID");
        System.clearProperty("FORCE_BROWSER");
    }

    @AfterMethod
    public void tearDown() throws IOException {
        grid.shutdown();
        System.clearProperty("SELENIUM_GRID");
        System.clearProperty("FORCE_BROWSER");
    }

    // -----------------------------------------------------------------------
    // No grid: FORCE_BROWSER is the whole answer. This is what CI runs on.
    // -----------------------------------------------------------------------

    @Test
    public void withoutAGridTheForcedBrowserIsTheOnlyOne() throws Exception {
        System.setProperty("FORCE_BROWSER", "10.0.0.7");

        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .containsExactly("10.0.0.7");
    }

    @Test
    public void withNeitherAGridNorAForcedBrowserTheMatrixHoldsOneEmptyEntry() throws Exception {
        // Not an empty iterator: HookGSpec.seleniumSetup relies on getting "" so it can fail with
        // "Non available browsers. Is it BrowsersDataProvider added in your test?"
        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .containsExactly("");
    }

    // -----------------------------------------------------------------------
    // A grid that cannot be read
    // -----------------------------------------------------------------------

    @Test
    public void aGridThatRefusesTheConnectionYieldsNoBrowsers() throws Exception {
        String dead = "127.0.0.1:" + grid.getPort();
        grid.shutdown();
        System.setProperty("SELENIUM_GRID", dead);

        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .containsExactly("");
    }

    @Test
    public void aGridConsoleWithNoNodesYieldsNoBrowsers() throws Exception {
        serveConsole("<html><body>no nodes registered</body></html>");

        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .containsExactly("");
    }

    // -----------------------------------------------------------------------
    // A grid console with free slots
    // -----------------------------------------------------------------------

    @Test
    public void onlyTheForcedBrowserIsPickedOutOfTheGrid() throws Exception {
        serveConsole(console(
                slot("chrome", "62.0", "LINUX"),
                slot("firefox", "57.0", "LINUX")));
        System.setProperty("FORCE_BROWSER", "chrome_62.0");

        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .as("the matrix is narrowed to the one browser the run asked for")
                .containsExactly("chrome_62.0");
    }

    @Test
    public void aGridBrowserThatWasNotForcedIsIgnored() throws Exception {
        serveConsole(console(slot("firefox", "57.0", "LINUX")));
        System.setProperty("FORCE_BROWSER", "chrome_62.0");

        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .containsExactly("");
    }

    @Test
    public void aNodeThatLostItsConnectionIsSkipped() throws Exception {
        serveConsole("<html><body><div class='proxy'>"
                + "<p class='proxyname'>Connection refused to node</p>"
                + "<div class='content_detail'><p>"
                + "<a title=\"{browserName=chrome, version=62.0, platform=LINUX}\">c</a>"
                + "</p></div></div></body></html>");
        System.setProperty("FORCE_BROWSER", "chrome_62.0");

        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .containsExactly("");
    }

    @Test
    public void theSameBrowserOnSeveralSlotsIsRepeatedUnlessUniquenessIsAskedFor() throws Exception {
        String page = console(
                slot("chrome", "62.0", "LINUX"),
                slot("chrome", "62.0", "LINUX"),
                slot("chrome", "62.0", "LINUX"));
        System.setProperty("FORCE_BROWSER", "chrome_62.0");

        serveConsole(page);
        assertThat(collect(BrowsersDataProvider.availableBrowsers(null, null)))
                .as("one entry per free slot, so the matrix can run them in parallel")
                .containsExactly("chrome_62.0", "chrome_62.0", "chrome_62.0");

        serveConsole(page);
        assertThat(collect(BrowsersDataProvider.availableUniqueBrowsers(null, null)))
                .containsExactly("chrome_62.0");
    }

    // -----------------------------------------------------------------------
    // The platformName filters
    // -----------------------------------------------------------------------

    @Test
    public void theIOSProviderKeepsOnlyIOSSlots() throws Exception {
        System.setProperty("FORCE_BROWSER", "safari_11.0");

        serveConsole(console(
                mobileSlot("safari", "11.0", "iOS"),
                mobileSlot("chrome", "62.0", "Android")));
        assertThat(collect(BrowsersDataProvider.availableIOSBrowsers(null, null)))
                .containsExactly("safari_11.0");

        System.setProperty("FORCE_BROWSER", "chrome_62.0");
        serveConsole(console(
                mobileSlot("safari", "11.0", "iOS"),
                mobileSlot("chrome", "62.0", "Android")));
        assertThat(collect(BrowsersDataProvider.availableIOSBrowsers(null, null)))
                .as("an Android slot is not an iOS one")
                .containsExactly("");
    }

    @Test
    public void theMobileProviderTakesBothAndroidAndIOS() throws Exception {
        System.setProperty("FORCE_BROWSER", "chrome_62.0");
        serveConsole(console(
                mobileSlot("safari", "11.0", "iOS"),
                mobileSlot("chrome", "62.0", "Android")));

        assertThat(collect(BrowsersDataProvider.availableMobileBrowsers(null, null)))
                .containsExactly("chrome_62.0");
    }

    @Test
    public void aDesktopSlotIsNotAMobileOne() throws Exception {
        System.setProperty("FORCE_BROWSER", "chrome_62.0");
        serveConsole(console(slot("chrome", "62.0", "LINUX")));

        assertThat(collect(BrowsersDataProvider.availableMobileBrowsers(null, null)))
                .containsExactly("");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void serveConsole(String html) {
        grid.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html));
        System.setProperty("SELENIUM_GRID", grid.getHostName() + ":" + grid.getPort());
    }

    /** A free slot, the way the Grid 3 console renders one: capabilities in the title attribute. */
    private static String slot(String browser, String version, String platform) {
        return "<p><a title=\"{browserName=" + browser + ", version=" + version
                + ", platform=" + platform + "}\">" + browser + "</a></p>";
    }

    private static String mobileSlot(String browser, String version, String platformName) {
        return "<p><a title=\"{browserName=" + browser + ", version=" + version
                + ", platformName=" + platformName + "}\">" + browser + "</a></p>";
    }

    private static String console(String... slots) {
        StringBuilder page = new StringBuilder("<html><body><div class='proxy'>"
                + "<p class='proxyname'>node http://10.0.0.7:5555</p>"
                + "<div class='content_detail'>");
        for (String slot : slots) {
            page.append(slot);
        }
        return page.append("</div></div></body></html>").toString();
    }

    private static List<String> collect(Iterator<String[]> rows) {
        List<String> flat = new ArrayList<>();
        while (rows.hasNext()) {
            flat.add(rows.next()[0]);
        }
        return flat;
    }
}
