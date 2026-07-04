package org.workfitai.authservice.service.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.exception.CannotUnlinkLastAuthMethodException;
import org.workfitai.authservice.exception.ProviderAlreadyLinkedException;
import org.workfitai.authservice.model.OAuthProvider;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.OAuthProviderRepository;
import org.workfitai.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OAuthProviderServiceTest {

    @Mock OAuthProviderRepository oauthProviderRepository;
    @Mock OAuthTokenService oauthTokenService;
    @Mock UserRepository userRepository;

    @InjectMocks OAuthProviderService service;

    @Test
    void saveProvider_encryptsPresentTokensAndSetsUpdatedAt() {
        OAuthProvider provider = provider(Provider.GOOGLE)
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
        when(oauthTokenService.encrypt("access-token")).thenReturn("encrypted-access");
        when(oauthTokenService.encrypt("refresh-token")).thenReturn("encrypted-refresh");
        when(oauthProviderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OAuthProvider saved = service.saveProvider(provider);

        assertThat(saved.getAccessToken()).isEqualTo("encrypted-access");
        assertThat(saved.getRefreshToken()).isEqualTo("encrypted-refresh");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void saveProvider_withoutTokensSkipsEncryption() {
        OAuthProvider provider = provider(Provider.GITHUB).build();
        when(oauthProviderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OAuthProvider saved = service.saveProvider(provider);

        assertThat(saved.getAccessToken()).isNull();
        assertThat(saved.getRefreshToken()).isNull();
        verify(oauthTokenService, never()).encrypt(any());
    }

    @Test
    void findByProviderAndProviderId_decryptsTokensWhenFound() {
        OAuthProvider stored = provider(Provider.GOOGLE)
                .accessToken("encrypted-access")
                .refreshToken("encrypted-refresh")
                .build();
        when(oauthProviderRepository.findByProviderAndProviderId(Provider.GOOGLE, "google-1"))
                .thenReturn(Optional.of(stored));
        when(oauthTokenService.decrypt("encrypted-access")).thenReturn("access-token");
        when(oauthTokenService.decrypt("encrypted-refresh")).thenReturn("refresh-token");

        Optional<OAuthProvider> result = service.findByProviderAndProviderId(Provider.GOOGLE, "google-1");

        assertThat(result).isPresent();
        assertThat(result.get().getAccessToken()).isEqualTo("access-token");
        assertThat(result.get().getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void findByUserId_decryptsEachProvider() {
        when(oauthProviderRepository.findByUserId("user-1")).thenReturn(List.of(
                provider(Provider.GOOGLE).accessToken("enc-google").build(),
                provider(Provider.GITHUB).refreshToken("enc-github").build()));
        when(oauthTokenService.decrypt("enc-google")).thenReturn("google-token");
        when(oauthTokenService.decrypt("enc-github")).thenReturn("github-refresh");

        List<OAuthProvider> providers = service.findByUserId("user-1");

        assertThat(providers).extracting(OAuthProvider::getAccessToken)
                .containsExactly("google-token", null);
        assertThat(providers).extracting(OAuthProvider::getRefreshToken)
                .containsExactly(null, "github-refresh");
    }

    @Test
    void linkProvider_whenAlreadyLinked_throwsProviderAlreadyLinked() {
        OAuthProvider provider = provider(Provider.GOOGLE).build();
        when(oauthProviderRepository.existsByUserIdAndProvider("user-1", Provider.GOOGLE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.linkProvider("user-1", provider))
                .isInstanceOf(ProviderAlreadyLinkedException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    void linkProvider_setsUserAndSavesWhenNotLinked() {
        OAuthProvider provider = provider(Provider.GITHUB).accessToken("token").build();
        when(oauthProviderRepository.existsByUserIdAndProvider("user-1", Provider.GITHUB))
                .thenReturn(false);
        when(oauthTokenService.encrypt("token")).thenReturn("encrypted");
        when(oauthProviderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OAuthProvider result = service.linkProvider("user-1", provider);

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getAccessToken()).isEqualTo("encrypted");
    }

    @Test
    void unlinkProvider_blocksOnlyAuthMethodWhenNoPasswordAndSingleProvider() {
        User user = User.builder().id("user-1").username("alice").password("").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(oauthProviderRepository.countByUserId("user-1")).thenReturn(1L);

        assertThatThrownBy(() -> service.unlinkProvider("user-1", Provider.GOOGLE))
                .isInstanceOf(CannotUnlinkLastAuthMethodException.class);
        verify(oauthProviderRepository, never()).deleteByUserIdAndProvider(any(), any());
    }

    @Test
    void unlinkProvider_allowsWhenUserHasPassword() {
        User user = User.builder().id("user-1").username("alice").password("hash").build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(oauthProviderRepository.countByUserId("user-1")).thenReturn(1L);

        service.unlinkProvider("user-1", Provider.GOOGLE);

        verify(oauthProviderRepository).deleteByUserIdAndProvider("user-1", Provider.GOOGLE);
    }

    @Test
    void unlinkProvider_allowsWhenMultipleProvidersRemainWithoutPassword() {
        User user = User.builder().id("user-1").username("alice").password(null).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(oauthProviderRepository.countByUserId("user-1")).thenReturn(2L);

        service.unlinkProvider("user-1", Provider.GITHUB);

        verify(oauthProviderRepository).deleteByUserIdAndProvider("user-1", Provider.GITHUB);
    }

    @Test
    void unlinkProvider_missingUserThrowsIllegalArgument() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlinkProvider("missing", Provider.GITHUB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getLinkedProviders_mapsSafeResponseFields() {
        OAuthProvider provider = provider(Provider.GOOGLE)
                .email("alice@gmail.com")
                .displayName("Alice")
                .profilePicture("https://avatar")
                .build();
        when(oauthProviderRepository.findByUserId("user-1")).thenReturn(List.of(provider));

        var responses = service.getLinkedProviders("user-1");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getProvider()).isEqualTo("GOOGLE");
        assertThat(responses.get(0).getEmail()).isEqualTo("alice@gmail.com");
        assertThat(responses.get(0).getDisplayName()).isEqualTo("Alice");
        assertThat(responses.get(0).getProfilePicture()).isEqualTo("https://avatar");
    }

    @Test
    void updateLastUsed_savesMatchingProviderOnly() {
        OAuthProvider google = provider(Provider.GOOGLE).build();
        OAuthProvider github = provider(Provider.GITHUB).build();
        when(oauthProviderRepository.findByUserId("user-1")).thenReturn(List.of(google, github));

        service.updateLastUsed("user-1", Provider.GITHUB);

        assertThat(github.getLastUsedAt()).isNotNull();
        assertThat(google.getLastUsedAt()).isNull();
        verify(oauthProviderRepository).save(github);
        verify(oauthProviderRepository, never()).save(google);
    }

    @Test
    void updateLastUsed_noMatchingProviderDoesNothing() {
        when(oauthProviderRepository.findByUserId("user-1"))
                .thenReturn(List.of(provider(Provider.GOOGLE).build()));

        service.updateLastUsed("user-1", Provider.GITHUB);

        verify(oauthProviderRepository, never()).save(any());
    }

    @Test
    void refreshTokens_updatesOptionalRefreshAndExpiryWhenPresent() {
        OAuthProvider provider = provider(Provider.GOOGLE).build();
        when(oauthProviderRepository.findByUserId("user-1")).thenReturn(List.of(provider));
        when(oauthTokenService.encrypt("new-access")).thenReturn("enc-access");
        when(oauthTokenService.encrypt("new-refresh")).thenReturn("enc-refresh");

        service.refreshTokens("user-1", Provider.GOOGLE, "new-access", "new-refresh", 3600L);

        assertThat(provider.getAccessToken()).isEqualTo("enc-access");
        assertThat(provider.getRefreshToken()).isEqualTo("enc-refresh");
        assertThat(provider.getTokenExpiry()).isNotNull();
        assertThat(provider.getUpdatedAt()).isNotNull();
        verify(oauthProviderRepository).save(provider);
    }

    @Test
    void refreshTokens_withNullRefreshAndExpiry_keepsOptionalFieldsNull() {
        OAuthProvider provider = provider(Provider.GITHUB).build();
        when(oauthProviderRepository.findByUserId("user-1")).thenReturn(List.of(provider));
        when(oauthTokenService.encrypt("new-access")).thenReturn("enc-access");

        service.refreshTokens("user-1", Provider.GITHUB, "new-access", null, null);

        assertThat(provider.getAccessToken()).isEqualTo("enc-access");
        assertThat(provider.getRefreshToken()).isNull();
        assertThat(provider.getTokenExpiry()).isNull();
        verify(oauthProviderRepository).save(provider);
    }

    @Test
    void deleteAllByUserId_deletesEachProvider() {
        OAuthProvider google = provider(Provider.GOOGLE).build();
        OAuthProvider github = provider(Provider.GITHUB).build();
        when(oauthProviderRepository.findByUserId("user-1")).thenReturn(List.of(google, github));

        service.deleteAllByUserId("user-1");

        verify(oauthProviderRepository).delete(google);
        verify(oauthProviderRepository).delete(github);
    }

    @Test
    void isProviderLinked_delegatesToRepository() {
        when(oauthProviderRepository.existsByUserIdAndProvider("user-1", Provider.GOOGLE))
                .thenReturn(true);

        assertThat(service.isProviderLinked("user-1", Provider.GOOGLE)).isTrue();
    }

    private OAuthProvider.OAuthProviderBuilder provider(Provider provider) {
        return OAuthProvider.builder()
                .id("provider-id")
                .userId("user-1")
                .provider(provider)
                .providerId(provider.name().toLowerCase() + "-id");
    }
}
