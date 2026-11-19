package krd.parliament.vms.web;

import krd.parliament.vms.service.AuditService;
import krd.parliament.vms.service.BackupService;
import krd.parliament.vms.service.SettingsService;
import krd.parliament.vms.service.messaging.EmailGateway;
import krd.parliament.vms.service.messaging.SmsGateway;
import krd.parliament.vms.service.messaging.WhatsAppGateway;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Parliament-wide settings and backups (system administrator). */
@Controller
public class SettingsController {

    private final SettingsService settings;
    private final BackupService backups;
    private final AuditService audit;
    private final SmsGateway sms;
    private final WhatsAppGateway whatsapp;
    private final EmailGateway email;
    private final Flash flash;

    public SettingsController(SettingsService settings, BackupService backups, AuditService audit,
                              SmsGateway sms, WhatsAppGateway whatsapp, EmailGateway email, Flash flash) {
        this.settings = settings;
        this.backups = backups;
        this.audit = audit;
        this.sms = sms;
        this.whatsapp = whatsapp;
        this.email = email;
        this.flash = flash;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("values", settings.all());
        model.addAttribute("smsConfigured", sms.isConfigured());
        model.addAttribute("whatsappConfigured", whatsapp.isConfigured());
        model.addAttribute("emailConfigured", email.isConfigured());
        return "settings/index";
    }

    @PostMapping("/settings")
    public String save(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        for (String key : SettingsService.DEFAULTS.keySet()) {
            String formKey = "s_" + key.replace('.', '_');
            if (SettingsService.BOOLEAN_KEYS.contains(key)) {
                settings.set(key, String.valueOf(params.containsKey(formKey)));
            } else if (params.containsKey(formKey)) {
                settings.set(key, params.get(formKey));
            }
        }
        audit.log(AuditService.SYSTEM, null, "SETTINGS_UPDATED", null);
        flash.ok(ra, "msg.saved");
        return "redirect:/settings";
    }

    @GetMapping("/admin/backups")
    public String backups(Model model) {
        model.addAttribute("backups", backups.list());
        return "settings/backups";
    }

    @PostMapping("/admin/backups")
    public String runBackup(RedirectAttributes ra) {
        try {
            flash.ok(ra, "msg.backupCreated", backups.runBackup());
        } catch (Exception e) {
            flash.error(ra, "error.backupFailed", e.getMessage());
        }
        return "redirect:/admin/backups";
    }

    @GetMapping("/admin/backups/{name}")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(backups.file(name)));
    }
}
