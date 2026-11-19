package krd.parliament.vms.service;

/** A rule violation shown to the user as a flash message. The message is an i18n key. */
public class BusinessException extends RuntimeException {

    private final Object[] args;

    public BusinessException(String messageKey, Object... args) {
        super(messageKey);
        this.args = args;
    }

    public Object[] getArgs() {
        return args;
    }
}
