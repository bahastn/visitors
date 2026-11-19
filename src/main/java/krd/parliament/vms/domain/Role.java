package krd.parliament.vms.domain;

/**
 * User roles. The system serves every office of the parliament; office-bound roles act on the
 * offices a user is assigned to, while ADMIN (and users marked "all offices") work across all offices.
 */
public enum Role {
    /** System administrator: all offices, users, settings, backups. */
    ADMIN,
    /** Head of an office: Speaker, Deputy Speaker, Secretary of Parliament, committee chair, MP... */
    HEAD,
    /** Office director / chief of staff: full control of the office's visits. */
    DIRECTOR,
    /** Secretary / scheduler: registers requests, recommends, manages the calendar. */
    SECRETARY,
    /** Reception / front desk: check-in, badges, passes (usually central for the whole building). */
    RECEPTION,
    /** Security officer: screening, watchlist, expected arrivals (usually central). */
    SECURITY,
    /** Read-only access to records and reports (e.g. Secretary-General, audit). */
    AUDITOR;

    /** Senior users that must use two-factor login when the setting is enabled. */
    public boolean isSenior() {
        return this == ADMIN || this == HEAD || this == DIRECTOR;
    }

    /** Users allowed to take the final approve / reject decision. */
    public boolean canDecide() {
        return this == HEAD || this == DIRECTOR || this == ADMIN;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
