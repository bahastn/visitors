package krd.parliament.vms.config;

import krd.parliament.vms.repo.UserRepository;
import krd.parliament.vms.security.TotpAuthenticationProvider;
import krd.parliament.vms.security.TotpService;
import krd.parliament.vms.security.TotpWebAuthenticationDetails;
import krd.parliament.vms.security.VmsUserDetails;
import krd.parliament.vms.security.VmsUserDetailsService;
import krd.parliament.vms.web.HomeController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public TotpAuthenticationProvider totpAuthenticationProvider(VmsUserDetailsService uds, PasswordEncoder enc, TotpService totp) {
        return new TotpAuthenticationProvider(uds, enc, totp);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TotpAuthenticationProvider provider,
                                           UserRepository users, LocaleResolver localeResolver) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        SavedRequestAwareAuthenticationSuccessHandler success = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                org.springframework.security.core.Authentication authentication)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                if (authentication.getPrincipal() instanceof VmsUserDetails d) {
                    users.findById(d.getId()).ifPresent(u -> {
                        u.setLastLoginAt(LocalDateTime.now());
                        u.setFailedLogins(0);
                        users.save(u);
                        if (u.getPreferredLanguage() != null) {
                            localeResolver.setLocale(request, response, Locale.forLanguageTag(u.getPreferredLanguage()));
                        }
                    });
                }
                // Deep link after login: the user did not land on "/", so the grid icon must show the app
                // home rather than jump to the role's default app (see HomeController).
                SavedRequest saved = requestCache.getRequest(request, response);
                if (saved != null && !isRoot(request, saved.getRedirectUrl())) {
                    request.getSession().setAttribute(HomeController.LANDED, Boolean.TRUE);
                }
                super.onAuthenticationSuccess(request, response, authentication);
            }
        };
        success.setDefaultTargetUrl("/");
        success.setRequestCache(requestCache);

        http
            .authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/public/**", "/vendor/**", "/css/**", "/js/**", "/img/**",
                        "/manifest.webmanifest", "/sw.js", "/offline.html", "/favicon.ico", "/error",
                        "/calendar/feed/**", "/api/v1/**").permitAll()
                .requestMatchers("/users/**", "/admin/**", "/settings/**").hasRole("ADMIN")
                .requestMatchers("/offices/**").hasAnyRole("ADMIN", "DIRECTOR")
                .requestMatchers("/watchlist/**", "/security/**").hasAnyRole("SECURITY", "ADMIN", "DIRECTOR")
                .requestMatchers("/reception/**", "/api/reception/**").hasAnyRole("RECEPTION", "SECRETARY", "SECURITY", "ADMIN", "DIRECTOR")
                .requestMatchers("/").authenticated()
                .requestMatchers("/dashboard", "/dashboard/**").hasAnyRole("HEAD", "DIRECTOR", "SECRETARY", "ADMIN", "AUDITOR")
                // Awesome bar JSON: session-authenticated staff only (not part of the external /api/v1 API)
                .requestMatchers("/api/search").authenticated()
                .requestMatchers("/reports/**").hasAnyRole("HEAD", "DIRECTOR", "ADMIN", "SECRETARY", "AUDITOR")
                .anyRequest().authenticated())
            .formLogin(f -> f
                .loginPage("/login")
                .authenticationDetailsSource(TotpWebAuthenticationDetails::new)
                .successHandler(success)
                .failureHandler((req, res, ex) -> {
                    String user = URLEncoder.encode(String.valueOf(req.getParameter("username")), StandardCharsets.UTF_8);
                    String code = switch (String.valueOf(ex.getMessage())) {
                        case "2FA_REQUIRED" -> "2fa";
                        case "2FA_INVALID" -> "2fa_invalid";
                        default -> ex instanceof org.springframework.security.authentication.DisabledException ? "disabled" : "bad";
                    };
                    res.sendRedirect(req.getContextPath() + "/login?error=" + code + "&u=" + user);
                })
                .permitAll())
            .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll())
            .csrf(c -> c.ignoringRequestMatchers("/api/v1/**"))
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()));
        return http.build();
    }

    /** True when a saved redirect URL points at the app root ("/" under the context path). */
    private static boolean isRoot(jakarta.servlet.http.HttpServletRequest request, String url) {
        try {
            String path = URI.create(url).getRawPath();
            String ctx = request.getContextPath();
            return path == null || path.isEmpty() || path.equals(ctx) || path.equals(ctx + "/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver r = new SessionLocaleResolver();
        r.setDefaultLocale(Locale.forLanguageTag("ckb"));
        return r;
    }
}
