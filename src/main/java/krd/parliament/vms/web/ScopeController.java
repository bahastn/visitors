package krd.parliament.vms.web;

import jakarta.servlet.http.HttpServletRequest;
import krd.parliament.vms.security.OfficeScope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Office switcher in the sidebar. */
@Controller
public class ScopeController {

    private final OfficeScope scope;

    public ScopeController(OfficeScope scope) {
        this.scope = scope;
    }

    @PostMapping("/scope")
    public String select(@RequestParam(required = false) Long officeId, HttpServletRequest req) {
        scope.select(officeId);
        return "redirect:" + GlobalAdvice.back(req);
    }
}
