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
package com.stratio.qa.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A tiny static site for the Selenium steps to drive.
 *
 * <p>The browser lives in its own container, so it cannot reach anything on {@code localhost}: the
 * server binds every interface and {@link #getAddress()} hands back an address the browser can
 * actually resolve -- the pod IP under CI, the host's LAN address when run locally against the
 * {@code bdt-chrome} container.
 *
 * <p>Serving the pages from here rather than pointing at a real site is what makes these tests
 * deterministic. The orphaned feature files this replaces aimed at www.google.es and
 * www.metabase.com and looked for markup ({@code id:searchform}, {@code name:f}) those sites
 * dropped years ago.
 */
public class SeleniumTestSite {

    private HttpServer server;

    private String address;

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> respond(exchange, MAIN_PAGE, true));
        server.createContext("/frame", exchange -> respond(exchange, FRAME_PAGE, false));
        server.createContext("/second", exchange -> respond(exchange, SECOND_PAGE, false));
        server.setExecutor(null);
        server.start();
        address = reachableHost() + ":" + server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * @return {@code host:port}, in the shape "My app is running in '...'" expects
     */
    public String getAddress() {
        return address;
    }

    private void respond(HttpExchange exchange, String body, boolean withCookies) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        if (withCookies) {
            exchange.getResponseHeaders().add("Set-Cookie", "bdtSession=abc123; Path=/");
            exchange.getResponseHeaders().add("Set-Cookie", "bdtFlavour=vanilla; Path=/");
        }
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    /**
     * The first site-local IPv4 address on a live interface. Loopback is useless here: it is the
     * browser container that has to connect back.
     */
    private static String reachableHost() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface nic : Collections.list(interfaces)) {
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                continue;
            }
            for (InetAddress candidate : Collections.list(nic.getInetAddresses())) {
                if (candidate.getAddress().length == 4 && !candidate.isLoopbackAddress()) {
                    return candidate.getHostAddress();
                }
            }
        }
        throw new SocketException("No non-loopback IPv4 address found; the browser container would "
                + "have nothing to connect back to");
    }

    private static final String MAIN_PAGE =
            "<html><head><title>BDT Selenium test site</title>\n"
            + "<style>#hidden-box{display:none}</style>\n"
            + "</head><body>\n"
            + "<h1 id='main-title' data-role='heading'>BDT test site</h1>\n"
            + "<p id='intro'>Everything the Selenium steps need, served locally.</p>\n"

            + "<input type='text' id='username' name='username' class='field' value=''/>\n"
            + "<input type='text' id='prefilled' name='prefilled' class='field' value='delete me please'/>\n"
            + "<input type='text' id='disabled-field' class='field' disabled='disabled' value='untouchable'/>\n"

            + "<select id='role' name='role'>\n"
            + "  <option value='reader'>reader</option>\n"
            + "  <option value='writer'>writer</option>\n"
            + "  <option value='admin'>admin</option>\n"
            + "</select>\n"
            + "<select id='tags' name='tags' multiple='multiple'>\n"
            + "  <option value='alpha' selected='selected'>alpha</option>\n"
            + "  <option value='beta' selected='selected'>beta</option>\n"
            + "</select>\n"

            + "<input type='checkbox' id='remember' name='remember'/>\n"
            + "<div id='hidden-box'>you cannot see me</div>\n"

            + "<button id='reveal-btn' onclick=\"setTimeout(function(){"
            + "document.getElementById('slow-result').style.display='block';},1500)\">reveal</button>\n"
            + "<div id='slow-result' style='display:none'>revealed at last</div>\n"

            + "<div id='click-counter' ondblclick=\"this.innerHTML='double clicked'\" "
            + "onclick=\"document.getElementById('click-echo').innerHTML='clicked'\">click me</div>\n"
            + "<div id='click-echo'>not clicked</div>\n"

            + "<div id='hover-target' onmouseover=\"document.getElementById('hover-echo')"
            + ".innerHTML='hovered'\">hover me</div>\n"
            + "<div id='hover-echo'>not hovered</div>\n"

            + "<div id='draggable' style='position:absolute;left:10px;top:400px;width:60px;height:60px;"
            + "background:#38b'>drag</div>\n"

            + "<a id='second-link' href='/second' target='_blank'>open second window</a>\n"
            + "<iframe id='frame-one' name='frame-one' src='/frame' width='300' height='120'></iframe>\n"
            + "</body></html>";

    private static final String FRAME_PAGE =
            "<html><body><span id='frame-content'>inside the iframe</span></body></html>";

    private static final String SECOND_PAGE =
            "<html><head><title>second window</title></head>"
            + "<body><span id='second-content'>the second window</span></body></html>";
}
