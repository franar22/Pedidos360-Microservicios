package com.pedidos360.catalog.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cognito entrega los grupos del usuario en el claim "cognito:groups".
 * Se homologan a authorities ROLE_* para usar @PreAuthorize("hasRole('ADMIN')").
 */
@Component
public class CognitoGroupsConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(defaultConverter.convert(jwt));

        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null) {
            authorities.addAll(
                    groups.stream()
                            .map(group -> new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                            .collect(Collectors.toList())
            );
        }

        return new JwtAuthenticationToken(jwt, authorities);
    }
}
