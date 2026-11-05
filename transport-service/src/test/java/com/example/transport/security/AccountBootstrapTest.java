package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AccountBootstrapTest {
    @Test
    void createsOnlyFirstAccountAndHashesPassword() {
        var accounts = mock(AccountRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var bootstrap =
                new AccountBootstrap(accounts, encoder, "first-admin", "bootstrap-password");
        bootstrap.run(new DefaultApplicationArguments());
        var captured = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(captured.capture());
        assertThat(encoder.matches("bootstrap-password", captured.getValue().getPasswordHash()))
                .isTrue();
        assertThat(captured.getValue().getRole()).isEqualTo("ADMIN");
        reset(accounts);
        when(accounts.count()).thenReturn(1L);
        bootstrap.run(new DefaultApplicationArguments());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void absentCredentialsDoNothingAndPartialOrWeakConfigurationFails() {
        var accounts = mock(AccountRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        new AccountBootstrap(accounts, encoder, "", "").run(new DefaultApplicationArguments());
        verifyNoInteractions(accounts);
        assertThatThrownBy(
                        () ->
                                new AccountBootstrap(accounts, encoder, "admin", "short")
                                        .run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }
}
