package com.territorial.user.global.security.oauth2;

import com.territorial.user.global.config.FrontendProperties;
import com.territorial.user.global.security.JwtTokenProvider;
import com.territorial.user.global.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** OAuth 성공 시 access·refresh 토큰을 발급하고 프론트 콜백으로 리다이렉트한다. */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String DEFAULT_ROLE = "USER";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final FrontendProperties frontendProperties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String accessToken =
                jwtTokenProvider.createAccessToken(oAuth2User.getUserId(), DEFAULT_ROLE);
        String refreshToken = jwtTokenProvider.createRefreshToken(oAuth2User.getUserId());
        refreshTokenService.save(oAuth2User.getUserId(), refreshToken);

        String redirectUrl =
                frontendProperties.callbackUrl()
                        + "?accessToken="
                        + accessToken
                        + "&refreshToken="
                        + refreshToken;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
