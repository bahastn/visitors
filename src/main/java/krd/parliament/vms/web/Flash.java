package krd.parliament.vms.web;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Translated success / error flash messages after a redirect. */
@Component
public class Flash {

    private final MessageSource messages;

    public Flash(MessageSource messages) {
        this.messages = messages;
    }

    public void ok(RedirectAttributes ra, String key, Object... args) {
        ra.addFlashAttribute("success", messages.getMessage(key, args, key, LocaleContextHolder.getLocale()));
    }

    public void error(RedirectAttributes ra, String key, Object... args) {
        ra.addFlashAttribute("error", messages.getMessage(key, args, key, LocaleContextHolder.getLocale()));
    }

    public void warn(RedirectAttributes ra, String key, Object... args) {
        ra.addFlashAttribute("warning", messages.getMessage(key, args, key, LocaleContextHolder.getLocale()));
    }
}
