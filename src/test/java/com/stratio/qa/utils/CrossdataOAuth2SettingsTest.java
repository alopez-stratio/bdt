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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stratio.qa.specs.DatabaseSpec;
import io.cucumber.java.en.Given;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

public class CrossdataOAuth2SettingsTest {

    private static final String ISSUER = "https://sis/sso/oidc";

    @Test
    public void aBareAccessTokenOnlySendsTheModeAndTheToken() {
        CrossdataOAuth2Settings settings = CrossdataOAuth2Settings.tokenPassthrough("AT", null, null, null, null, null);

        assertThat(settings.authMode()).isEqualTo("token_passthrough");
        assertThat(settings.connectionProperties()).isEqualTo(";AUTH_MODE=token_passthrough;OAUTH2_ACCESS_TOKEN=AT");
    }

    @Test
    public void everyGivenValueBecomesAConnectionProperty() {
        assertThat(CrossdataOAuth2Settings.tokenPassthrough("AT", "RT", ISSUER, "virtualizer-dbc", "openid offline_access", "https://sis/token")
                .connectionProperties())
                .isEqualTo(";AUTH_MODE=token_passthrough;OAUTH2_ACCESS_TOKEN=AT;OAUTH2_REFRESH_TOKEN=RT"
                        + ";OAUTH2_ISSUER=" + ISSUER + ";OAUTH2_CLIENT_ID=virtualizer-dbc;OAUTH2_SCOPE=openid offline_access"
                        + ";OAUTH2_TOKEN_ENDPOINT=https://sis/token");
    }

    @Test
    public void theAccessTokenIsMandatory() {
        assertThatThrownBy(() -> CrossdataOAuth2Settings.tokenPassthrough(null, "RT", ISSUER, "c", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access token is mandatory");
        assertThatThrownBy(() -> CrossdataOAuth2Settings.tokenPassthrough("", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void aRefreshTokenNeedsTheClientIdAndWhereToRenewIt() {
        assertThatThrownBy(() -> CrossdataOAuth2Settings.tokenPassthrough("AT", "RT", ISSUER, null, null, null))
                .hasMessageContaining("client id");
        assertThatThrownBy(() -> CrossdataOAuth2Settings.tokenPassthrough("AT", "RT", null, "c", null, null))
                .hasMessageContaining("issuer or the token endpoint");

        assertThat(CrossdataOAuth2Settings.tokenPassthrough("AT", "RT", null, "c", null, "https://sis/token").connectionProperties())
                .contains(";OAUTH2_TOKEN_ENDPOINT=https://sis/token");
    }

    @Test
    public void aValueCannotBreakTheConnectionString() {
        assertThatThrownBy(() -> CrossdataOAuth2Settings.tokenPassthrough("AT;PWD=x", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAUTH2_ACCESS_TOKEN cannot contain ';'");
    }

    @Test
    public void secretsAreMaskedAndTheRestIsKept() {
        String url = "jdbc:crossdata://host:13422/default?UID=user;PAGINATION=false;SSL=true;KEYSTORE=/ks.jks;KEYSTORE_PWD=ksp"
                + ";TRUSTSTORE=/ts.jks;TRUSTSTORE_PWD=tsp;PWD=pwd;LIMIT=-1;AUTH_MODE=token_passthrough;OAUTH2_ACCESS_TOKEN=eyJ.a.b"
                + ";OAUTH2_REFRESH_TOKEN=RT;OAUTH2_ISSUER=" + ISSUER + ";OAUTH2_CLIENT_ID=c;OAUTH2_CLIENT_SECRET=s";

        assertThat(CrossdataOAuth2Settings.maskSecrets(url))
                .isEqualTo("jdbc:crossdata://host:13422/default?UID=user;PAGINATION=false;SSL=true;KEYSTORE=/ks.jks;KEYSTORE_PWD=***"
                        + ";TRUSTSTORE=/ts.jks;TRUSTSTORE_PWD=***;PWD=***;LIMIT=-1;AUTH_MODE=token_passthrough;OAUTH2_ACCESS_TOKEN=***"
                        + ";OAUTH2_REFRESH_TOKEN=***;OAUTH2_ISSUER=" + ISSUER + ";OAUTH2_CLIENT_ID=c;OAUTH2_CLIENT_SECRET=***");
        assertThat(CrossdataOAuth2Settings.maskSecrets(null)).isNull();
    }

    @Test
    public void theTokenPassthroughStepTakesEveryOptionalValue() throws Exception {
        Method method = Arrays.stream(DatabaseSpec.class.getMethods())
                .filter(m -> m.getName().equals("configureCrossdataOAuth2TokenPassthrough")).findFirst().orElseThrow();
        Pattern pattern = Pattern.compile(method.getAnnotation(Given.class).value());
        String head = "I configure the Crossdata JDBC connection with OAuth2 token passthrough: access token 'AT'";

        // 1 access token, 2 refresh token, 3 issuer, 4 client id, 5 scope, 6 token endpoint
        Matcher minimal = pattern.matcher(head);
        assertThat(minimal.matches()).isTrue();
        assertThat(minimal.groupCount()).isEqualTo(method.getParameterCount());
        assertThat(minimal.group(1)).isEqualTo("AT");
        for (int i = 2; i <= 6; i++) {
            assertThat(minimal.group(i)).isNull();
        }

        Matcher full = pattern.matcher(head + ", refresh token 'RT', issuer '" + ISSUER + "', client id 'virtualizer-dbc'"
                + ", scope 'openid login_data offline_access', token endpoint 'https://sis/token'");
        assertThat(full.matches()).isTrue();
        assertThat(full.group(2)).isEqualTo("RT");
        assertThat(full.group(3)).isEqualTo(ISSUER);
        assertThat(full.group(4)).isEqualTo("virtualizer-dbc");
        assertThat(full.group(5)).isEqualTo("openid login_data offline_access");
        assertThat(full.group(6)).isEqualTo("https://sis/token");
    }
}
