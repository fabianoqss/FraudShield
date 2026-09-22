package com.fraudetection.ledger_service.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TokenTypeAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String USER_ROLE = "USER";

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String USER_TOKEN_TYPE = "user";

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
        if (USER_TOKEN_TYPE.equals(jwt.getClaimAsString(TOKEN_TYPE_CLAIM))) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + USER_ROLE));
        }
        return authorities;
    }
}
