package com.ledger.identity;

import com.ledger.shared.LedgerPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName()
        );
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(
            @Valid @RequestBody AuthService.RegisterRequest request,
            HttpServletRequest httpReq,
            HttpServletResponse httpRes
    ) {
        return authService.register(request, httpReq, httpRes);
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(
            @Valid @RequestBody AuthService.LoginRequest request,
            HttpServletRequest httpReq,
            HttpServletResponse httpRes
    ) {
        return authService.login(request, httpReq, httpRes);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse httpRes) {
        authService.logout(LedgerPrincipal.current(), httpRes);
    }

    @GetMapping("/me")
    public AuthService.AuthResponse me() {
        return authService.currentUser(LedgerPrincipal.current());
    }

    @PostMapping("/switch-organization")
    public AuthService.AuthResponse switchOrganization(@Valid @RequestBody SwitchOrgRequest req) {
        return authService.switchOrganization(LedgerPrincipal.current(), req.organizationId());
    }

    @GetMapping("/sessions")
    public List<AuthService.SessionDto> listSessions() {
        return authService.listSessions(LedgerPrincipal.current());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID sessionId) {
        authService.revokeSession(LedgerPrincipal.current(), sessionId);
    }

    @PatchMapping("/profile")
    public AuthService.AuthResponse updateProfile(@Valid @RequestBody AuthService.UpdateProfileRequest req) {
        return authService.updateProfile(LedgerPrincipal.current(), req);
    }

    public record SwitchOrgRequest(@NotNull UUID organizationId) {}
}
