package com.example.ludo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class GoogleTokenVerifier {
    private final JwtDecoder decoder;
    public GoogleTokenVerifier(@Value("${google.client-id:}") String clientId){
        if(clientId==null||clientId.isBlank()) throw new IllegalStateException("google.client-id must be configured before Google login is enabled");
        JwtDecoder d=JwtDecoders.fromIssuerLocation("https://accounts.google.com");
        OAuth2TokenValidator<Jwt> issuer=new JwtIssuerValidator("https://accounts.google.com");
        OAuth2TokenValidator<Jwt> audience=jwt->{List<String> aud=jwt.getAudience();return aud!=null&&aud.contains(clientId)?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Google audience does not match configured client ID",null));};
        OAuth2TokenValidator<Jwt> validator=new DelegatingOAuth2TokenValidator<>(issuer,audience);
        ((org.springframework.security.oauth2.jwt.NimbusJwtDecoder)d).setJwtValidator(validator);
        this.decoder=d;
    }
    public Jwt verify(String credential){return decoder.decode(credential);}
}
