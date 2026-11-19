package krd.parliament.vms.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Username + password, plus the authenticator code for users who enabled two-factor login. */
public class TotpAuthenticationProvider extends DaoAuthenticationProvider {

    private final TotpService totp;

    public TotpAuthenticationProvider(UserDetailsService uds, PasswordEncoder encoder, TotpService totp) {
        super();
        setPasswordEncoder(encoder);
        setUserDetailsService(uds);
        this.totp = totp;
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails userDetails, UsernamePasswordAuthenticationToken auth)
            throws AuthenticationException {
        super.additionalAuthenticationChecks(userDetails, auth);
        if (userDetails instanceof VmsUserDetails u && u.isTotpEnabled()) {
            String code = auth.getDetails() instanceof TotpWebAuthenticationDetails d ? d.getTotpCode() : null;
            if (code == null || code.isBlank()) {
                throw new BadCredentialsException("2FA_REQUIRED");
            }
            if (!totp.verify(u.getTotpSecret(), code)) {
                throw new BadCredentialsException("2FA_INVALID");
            }
        }
    }
}
