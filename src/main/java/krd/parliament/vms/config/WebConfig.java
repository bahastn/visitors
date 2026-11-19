package krd.parliament.vms.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import krd.parliament.vms.domain.Role;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.service.SettingsService;
import krd.parliament.vms.util.Labels;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.TimeZoneAwareLocaleContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleContextResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.TimeZone;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] STATIC = {"/vendor/**", "/css/**", "/js/**", "/img/**", "/error", "/sw.js",
            "/manifest.webmanifest", "/offline.html", "/public/**", "/api/v1/**", "/calendar/feed/**", "/login", "/logout"};

    private final CurrentUser currentUser;
    private final SettingsService settings;

    public WebConfig(CurrentUser currentUser, SettingsService settings) {
        this.currentUser = currentUser;
        this.settings = settings;
    }

    /**
     * Latin digits everywhere (bug 5): every LocaleResolver bean (the session resolver in SecurityConfig) is
     * wrapped so the request locale carries the Unicode extension nu-latn ("ckb-u-nu-latn"). Language,
     * message bundle lookup, ?lang= switching and session persistence are unchanged (the delegate stores
     * the plain locale); only number formatting (MessageFormat, #numbers, NumberFormat) switches to 0-9.
     * Static so it does not force early creation of this configuration's dependencies.
     */
    @Bean
    public static BeanPostProcessor latinDigitsLocaleResolverPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof LocaleResolver r && !(bean instanceof LatinDigitsLocaleResolver)) {
                    return new LatinDigitsLocaleResolver(r);
                }
                return bean;
            }
        };
    }

    /**
     * Same settings as Spring Boot's auto-configured message source (spring.messages.*), but message
     * arguments are always formatted with Latin digits, also outside a web request (SMS, e-mail, jobs).
     */
    @Bean
    public static MessageSource messageSource(Environment env) {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource() {
            @Override
            protected MessageFormat createMessageFormat(String msg, Locale locale) {
                return super.createMessageFormat(msg, Labels.latn(locale));
            }
        };
        ms.setBasenames(StringUtils.commaDelimitedListToStringArray(
                StringUtils.trimAllWhitespace(env.getProperty("spring.messages.basename", "i18n/messages"))));
        ms.setDefaultEncoding(env.getProperty("spring.messages.encoding", "UTF-8"));
        ms.setFallbackToSystemLocale(env.getProperty("spring.messages.fallback-to-system-locale", Boolean.class, false));
        return ms;
    }

    /** Decorates a LocaleResolver: resolved locales get nu-latn; setLocale is passed through unchanged. */
    public static class LatinDigitsLocaleResolver implements LocaleContextResolver {

        private final LocaleResolver delegate;

        public LatinDigitsLocaleResolver(LocaleResolver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            return Labels.latn(delegate.resolveLocale(request));
        }

        @Override
        public void setLocale(HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
            delegate.setLocale(request, response, locale);
        }

        @Override
        public LocaleContext resolveLocaleContext(HttpServletRequest request) {
            // Lazy, like SessionLocaleResolver: a ?lang= switch later in the request is still seen.
            return new TimeZoneAwareLocaleContext() {
                @Override
                public Locale getLocale() {
                    if (delegate instanceof LocaleContextResolver lcr) {
                        return Labels.latn(lcr.resolveLocaleContext(request).getLocale());
                    }
                    return resolveLocale(request);
                }

                @Override
                @Nullable
                public TimeZone getTimeZone() {
                    if (delegate instanceof LocaleContextResolver lcr
                            && lcr.resolveLocaleContext(request) instanceof TimeZoneAwareLocaleContext tz) {
                        return tz.getTimeZone();
                    }
                    return null;
                }
            };
        }

        @Override
        public void setLocaleContext(HttpServletRequest request, @Nullable HttpServletResponse response,
                                     @Nullable LocaleContext localeContext) {
            if (delegate instanceof LocaleContextResolver lcr) {
                lcr.setLocaleContext(request, response, localeContext);
            } else {
                delegate.setLocale(request, response, localeContext == null ? null : localeContext.getLocale());
            }
        }

        public LocaleResolver getDelegate() {
            return delegate;
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        registry.addInterceptor(lci);

        // Senior users must set up two-factor login when the setting requires it.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                var d = currentUser.details();
                if (d.isPresent() && d.get().getRole().isSenior() && !d.get().isTotpEnabled()
                        && settings.getBool(SettingsService.REQUIRE_2FA_SENIOR)) {
                    res.sendRedirect(req.getContextPath() + "/profile/2fa?required");
                    return false;
                }
                return true;
            }
        }).addPathPatterns("/**").excludePathPatterns(STATIC).excludePathPatterns("/profile/**", "/api/**");

        // Auditors have read-only access.
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                if ("POST".equalsIgnoreCase(req.getMethod()) && currentUser.role() == Role.AUDITOR) {
                    String p = req.getRequestURI();
                    if (!(p.startsWith("/profile") || p.startsWith("/notifications") || p.startsWith("/scope"))) {
                        res.sendError(HttpServletResponse.SC_FORBIDDEN);
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/**").excludePathPatterns(STATIC);
    }
}
