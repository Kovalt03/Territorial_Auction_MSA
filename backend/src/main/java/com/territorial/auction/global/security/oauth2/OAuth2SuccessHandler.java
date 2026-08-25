package com.territorial.auction.global.security.oauth2;

import com.territorial.auction.global.config.FrontendProperties;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.jwt.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final FrontendProperties frontendProperties;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();

        String accessToken = jwtTokenProvider.createAccessToken(oAuth2User.getUserId());
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
