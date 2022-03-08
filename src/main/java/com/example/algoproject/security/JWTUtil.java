package com.example.algoproject.security;

import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

@RequiredArgsConstructor
@Component
public class JWTUtil {

    @Value("${jwt.secret}")
    private String key;

    private final CustomUserDetailsService customUserDetailsService;

    // key Base64로 인코딩
    @PostConstruct
    protected void init() {
        key = Base64.getEncoder().encodeToString(key.getBytes());
    }


    // id로 JWT token 만듦
    public String makeJWT(String id) {
        Date now = new Date();

        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + Duration.ofMinutes(30).toMillis()))
                .claim("id", id)
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    // JWT 토큰에서 인증(유저) 정보 조회
    public Authentication getAuthentication(String accessToken) {
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(this.getJWTId(accessToken));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    // JWT token에서 id 파싱
    public String getJWTId(String JWT) {
        return (String) Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(JWT)
                .getBody().get("id").toString();
    }

    // header에서 token 값 추출
    public String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return token.substring("Bearer ".length());
    }

    // 토큰 유효성 and 만료일자 확인
    public boolean validateToken(String jwtToken) {
        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(key).parseClaimsJws(jwtToken);
            return !claims.getBody().getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}