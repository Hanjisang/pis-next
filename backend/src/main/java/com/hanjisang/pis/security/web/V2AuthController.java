package com.hanjisang.pis.security.web;

import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanjisang.pis.security.AuthenticatedUser;
import com.hanjisang.pis.security.AuthIdentityRepository;
import com.hanjisang.pis.security.AuthenticationContext;
import com.hanjisang.pis.security.AuthenticationSessionFilter;
import com.hanjisang.pis.security.AuthenticationSessionStore;
import com.hanjisang.pis.security.DoctorIdentity;
import com.hanjisang.pis.security.DoctorIdentityResolver;
import com.hanjisang.pis.security.OrganizationContext;
import com.hanjisang.pis.security.P15BusinessException;
import com.hanjisang.pis.security.JdbcAuditEventRepository;
import com.hanjisang.pis.security.P15AuthorizationService;

@RestController
@RequestMapping("/api/v2/auth")
public class V2AuthController {

    private final AuthIdentityRepository identities;
    private final AuthenticationSessionStore sessions;
    private final DoctorIdentityResolver doctorIdentityResolver;
    private final boolean requireAuthentication;
    private final boolean secureCookie;
    private final P15AuthorizationService authorization;
    private final JdbcAuditEventRepository audit;

    public V2AuthController(AuthIdentityRepository identities, AuthenticationSessionStore sessions,
            DoctorIdentityResolver doctorIdentityResolver,
            @Value("${pis.require-auth:false}") boolean requireAuthentication,
            @Value("${pis.auth-cookie-secure:false}") boolean secureCookie,
            P15AuthorizationService authorization, JdbcAuditEventRepository audit) {
        this.identities = identities;
        this.sessions = sessions;
        this.doctorIdentityResolver = doctorIdentityResolver;
        this.requireAuthentication = requireAuthentication;
        this.secureCookie = secureCookie;
        this.authorization = authorization;
        this.audit = audit;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(requireAuthentication);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        AuthenticatedUser user = identities.authenticate(request.username(), request.password())
                .orElseThrow(() -> new P15BusinessException("V2-AUTH-INVALID-CREDENTIALS", "用户名或密码错误", 401));
        String token = sessions.create(user);
        response.addHeader("Set-Cookie", ResponseCookie.from(AuthenticationSessionFilter.COOKIE_NAME, token)
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(8 * 60 * 60).build().toString());
        return AuthResponse.from(user, doctorIdentityResolver);
    }

    @GetMapping("/me")
    public AuthResponse me() {
        return AuthenticationContext.current().map(user -> AuthResponse.from(user, doctorIdentityResolver))
                .orElseThrow(() -> new P15BusinessException("V2-AUTHENTICATION-REQUIRED", "请先登录", 401));
    }

    @GetMapping("/doctors")
    public List<DoctorResponse> doctors() {
        AuthenticatedUser current = AuthenticationContext.current()
                .orElseThrow(() -> new P15BusinessException("V2-AUTHENTICATION-REQUIRED", "请先登录", 401));
        return identities.findEnabledDoctors(current.hospitalScope()).stream().map(DoctorResponse::from).toList();
    }

    @PostMapping("/logout")
    public void logout(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        String token = cookie(request, AuthenticationSessionFilter.COOKIE_NAME);
        sessions.remove(token);
        response.addHeader("Set-Cookie", ResponseCookie.from(AuthenticationSessionFilter.COOKIE_NAME, "")
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/").maxAge(0).build().toString());
    }

    @PostMapping("/password")
    public void changePassword(@RequestBody PasswordChangeRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        AuthenticatedUser current = AuthenticationContext.current()
                .orElseThrow(() -> new P15BusinessException("V2-AUTHENTICATION-REQUIRED", "请先登录", 401));
        validatePassword(request.newPassword());
        if (Objects.equals(request.currentPassword(), request.newPassword())) {
            throw new P15BusinessException("V2-AUTH-PASSWORD-UNCHANGED", "新密码不能与当前密码相同", 400);
        }
        if (!identities.changePassword(current.userId(), request.currentPassword(), request.newPassword())) {
            throw new P15BusinessException("V2-AUTH-PASSWORD-INVALID", "当前密码不正确", 400);
        }
        sessions.removeForUser(current.userId(), cookie(httpRequest, AuthenticationSessionFilter.COOKIE_NAME));
        audit.append("PIS-V2-AUTH-PASSWORD-CHANGE", "AUTHENTICATED_USER", new com.hanjisang.pis.security.ActorContext(
                current.userId().toString(), "AUTH_USER", "APPLICATION", current.permissions(), current.hospitalScope(),
                current.departmentScope(), current.taskScope()), "ALLOWED", "COMPLETED", current.userId(), "V2-AUTH-USER",
                UUID.randomUUID().toString(), "用户修改密码");
    }

    @PostMapping("/users/{userId}/password-reset")
    public void resetPassword(@org.springframework.web.bind.annotation.PathVariable UUID userId,
            @RequestBody PasswordResetRequest request) {
        var actor = authorization.require("P14-PERM-001");
        validatePassword(request.newPassword());
        if (!identities.resetPassword(userId, request.newPassword())) {
            throw new P15BusinessException("V2-AUTH-USER-NOT-FOUND", "用户不存在", 404);
        }
        sessions.removeForUser(userId, null);
        audit.append("PIS-V2-AUTH-PASSWORD-RESET", "P14-PERM-001", actor, "ALLOWED", "COMPLETED", userId,
                "V2-AUTH-USER", UUID.randomUUID().toString(), "管理员重置密码");
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new P15BusinessException("V2-AUTH-PASSWORD-WEAK", "密码至少需要 8 个字符", 400);
        }
    }

    private static String cookie(jakarta.servlet.http.HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public record LoginRequest(String username, String password) { }
    public record PasswordChangeRequest(String currentPassword, String newPassword) { }
    public record PasswordResetRequest(String newPassword) { }

    public record AuthConfigResponse(boolean required) { }

    public record AuthResponse(UUID userId, String username, String displayName, String roleCode,
            String department, Set<String> permissions, DoctorResponse doctor, OrganizationResponse organization) {
        static AuthResponse from(AuthenticatedUser user, DoctorIdentityResolver resolver) {
            DoctorResponse doctor = resolver.resolve(user).map(DoctorResponse::from).orElse(null);
            return new AuthResponse(user.userId(), user.username(), user.displayName(), user.roleCode(),
                    user.departmentScope(), user.permissions(), doctor, OrganizationResponse.from(user.organization()));
        }
    }

    public record DoctorResponse(UUID id, String doctorCode, String displayName, String title, String department) {
        static DoctorResponse from(DoctorIdentity doctor) {
            return new DoctorResponse(doctor.id(), doctor.doctorCode(), doctor.displayName(), doctor.title(),
                    doctor.department());
        }
    }

    public record OrganizationResponse(UUID hospitalProfileId, String hospitalProfileCode, UUID campusId,
            String campusCode, UUID departmentId, String departmentCode, String departmentName) {
        static OrganizationResponse from(OrganizationContext organization) {
            return organization == null ? null : new OrganizationResponse(organization.hospitalProfileId(),
                    organization.hospitalProfileCode(), organization.campusId(), organization.campusCode(),
                    organization.departmentId(), organization.departmentCode(), organization.departmentName());
        }
    }
}
