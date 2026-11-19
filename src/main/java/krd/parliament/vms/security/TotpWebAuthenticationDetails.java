package krd.parliament.vms.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** Carries the 6-digit authenticator code typed on the login form. */
public class TotpWebAuthenticationDetails extends WebAuthenticationDetails {

    private final String totpCode;

    public TotpWebAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.totpCode = request.getParameter("code");
    }

    public String getTotpCode() {
        return totpCode;
    }
}
