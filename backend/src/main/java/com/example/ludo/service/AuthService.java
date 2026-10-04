package com.example.ludo.service;

import com.example.ludo.model.User;
import com.example.ludo.repository.UserRepository;
import com.example.ludo.security.GoogleTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final GoogleTokenVerifier google;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Otp> otpCache=new ConcurrentHashMap<>();
    private final long otpMinutes;
    private final String smsProvider;
    public AuthService(UserRepository users,JdbcTemplate jdbc,GoogleTokenVerifier google,@Value("${auth.otp.expiry-minutes:5}") long otpMinutes,@Value("${auth.sms.provider:console}") String smsProvider){this.users=users;this.jdbc=jdbc;this.google=google;this.otpMinutes=otpMinutes;this.smsProvider=smsProvider;}
    public record AuthResult(String token,String userId,String name,String email,String mobile,boolean guest){}
    private record Otp(String code,Instant expiresAt){}
    public void sendOtp(String mobile){String normalized=normalizeMobile(mobile);String code=String.format("%06d",random.nextInt(1000000));otpCache.put(normalized,new Otp(code,Instant.now().plusSeconds(otpMinutes*60)));if("console".equalsIgnoreCase(smsProvider))System.out.println("[LUDO DEV OTP] "+normalized+" -> "+code);else throw new IllegalStateException("SMS provider is not implemented/configured: "+smsProvider);}
    @Transactional
    public AuthResult verifyOtp(String mobile,String code,String name){String normalized=normalizeMobile(mobile);Otp saved=otpCache.get(normalized);if(saved==null||saved.expiresAt().isBefore(Instant.now())||!saved.code().equals(code))throw new IllegalArgumentException("Invalid or expired OTP");otpCache.remove(normalized);User user=users.findByMobile(normalized);if(user==null)user=users.create(cleanName(name,"Player"),null,normalized,"MOBILE",normalized,false);else if(name!=null&&!name.isBlank())user=users.updateName(user.id(),cleanName(name,user.name()));return session(user);}
    @Transactional
    public AuthResult google(String credential){Jwt jwt=google.verify(credential);String sub=jwt.getSubject();String email=jwt.getClaimAsString("email");String name=jwt.getClaimAsString("name");if(sub==null||email==null)throw new IllegalArgumentException("Google token does not contain required account information");User user=users.findByProviderId("GOOGLE",sub);if(user==null)user=users.findByEmail(email);if(user==null)user=users.create(cleanName(name,"Google Player"),email,null,"GOOGLE",sub,false);return session(user);}
    @Transactional
    public AuthResult guest(String name){User user=users.create(cleanName(name,"Guest"),null,null,"GUEST",UUID.randomUUID().toString(),true);return session(user);}
    private AuthResult session(User user){String token=UUID.randomUUID().toString();jdbc.update("insert into auth_sessions(token,user_id,expires_at,created_at) values(?,?,DATEADD('DAY',30,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP)",token,user.id());return new AuthResult(token,user.id(),user.name(),user.email(),user.mobile(),user.guest());}
    private String normalizeMobile(String mobile){if(mobile==null||!mobile.matches("^\\+?[1-9]\\d{9,14}$"))throw new IllegalArgumentException("Enter a valid mobile number with country code, for example +919876543210");return mobile.startsWith("+")?mobile:"+"+mobile;}
    private String cleanName(String name,String fallback){String n=name==null?"":name.trim();return n.isBlank()?fallback:n.substring(0,Math.min(n.length(),30));}
}
