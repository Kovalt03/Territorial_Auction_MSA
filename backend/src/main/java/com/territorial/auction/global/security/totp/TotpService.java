package com.territorial.auction.global.security.totp;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Component;

@Component
public class TotpService {

    private static final String ISSUER = "Territorial Auction";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    public String generateSecret() {
        return secretGenerator.generate();
    }

    // 인증 앱 등록용 otpauth:// URI (프론트에서 QR로 렌더)
    public String buildOtpAuthUri(String secret, String accountName) {
        QrData data =
                new QrData.Builder()
                        .label(accountName)
                        .secret(secret)
                        .issuer(ISSUER)
                        .algorithm(HashingAlgorithm.SHA1)
                        .digits(6)
                        .period(30)
                        .build();
        return data.getUri();
    }

    public boolean verify(String secret, String code) {
        return code != null && codeVerifier.isValidCode(secret, code);
    }
}
