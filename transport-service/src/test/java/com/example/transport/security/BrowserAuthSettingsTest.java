package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class BrowserAuthSettingsTest {
    @Test
    void disabledByDefaultButEnablingRequiresHttpsOriginsAndFifteenMinuteAccessTokens() {
        var jwt = mock(JwtService.class);
        when(jwt.expiresIn()).thenReturn(900L);
        assertThat(new BrowserAuthSettings(false, "", jwt).enabled()).isFalse();
        assertThat(
                        new BrowserAuthSettings(
                                        true,
                                        "https://app.test.invalid,https://app.test.invalid:8443",
                                        jwt)
                                .origins())
                .hasSize(2);
        for (String invalid :
                new String[] {
                    "",
                    "*",
                    "https://*.test.invalid",
                    "http://localhost:3000",
                    "https://app.test.invalid/",
                    "https://user@app.test.invalid",
                    "https://app.test.invalid?x",
                    "https://app.test.invalid#x",
                    "https://app.test.invalid:99999"
                })
            assertThatThrownBy(() -> new BrowserAuthSettings(true, invalid, jwt))
                    .isInstanceOf(IllegalArgumentException.class);
        when(jwt.expiresIn()).thenReturn(3600L);
        assertThatThrownBy(() -> new BrowserAuthSettings(true, "https://app.test.invalid", jwt))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
