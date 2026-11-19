package krd.parliament.vms.web;

import krd.parliament.vms.domain.Enums.NotificationChannel;
import krd.parliament.vms.domain.Notification;
import krd.parliament.vms.repo.NotificationRepository;
import krd.parliament.vms.security.CurrentUser;
import krd.parliament.vms.service.NotificationService;
import krd.parliament.vms.service.NotFoundException;
import krd.parliament.vms.service.messaging.EmailGateway;
import krd.parliament.vms.service.messaging.SmsGateway;
import krd.parliament.vms.service.messaging.WhatsAppGateway;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationController {

    private final NotificationRepository repo;
    private final NotificationService service;
    private final CurrentUser currentUser;
    private final SmsGateway sms;
    private final WhatsAppGateway whatsapp;
    private final EmailGateway email;
    private final Flash flash;

    public NotificationController(NotificationRepository repo, NotificationService service, CurrentUser currentUser,
                                  SmsGateway sms, WhatsAppGateway whatsapp, EmailGateway email, Flash flash) {
        this.repo = repo;
        this.service = service;
        this.currentUser = currentUser;
        this.sms = sms;
        this.whatsapp = whatsapp;
        this.email = email;
        this.flash = flash;
    }

    @GetMapping("/notifications")
    public String mine(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", repo.findByRecipientUserOrderByCreatedAtDesc(currentUser.get(), PageRequest.of(page, 30)));
        return "notifications/mine";
    }

    /** Opening a notification marks it read and jumps to its link. */
    @GetMapping("/notifications/{id}")
    @Transactional
    public String open(@PathVariable Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> new NotFoundException("Notification"));
        if (n.getRecipientUser() != null && n.getRecipientUser().getId().equals(currentUser.require().getId())) {
            n.setReadFlag(true);
        }
        return "redirect:" + (n.getLink() != null && n.getLink().startsWith("/") ? n.getLink() : "/notifications");
    }

    @PostMapping("/notifications/read-all")
    @Transactional
    public String readAll() {
        repo.markAllRead(currentUser.require());
        return "redirect:/notifications";
    }

    /** Log of every SMS / WhatsApp / email sent to visitors. */
    @GetMapping("/notifications/log")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY')")
    public String log(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", repo.findByChannelNotOrderByCreatedAtDesc(NotificationChannel.IN_APP, PageRequest.of(page, 50)));
        model.addAttribute("smsConfigured", sms.isConfigured());
        model.addAttribute("whatsappConfigured", whatsapp.isConfigured());
        model.addAttribute("emailConfigured", email.isConfigured());
        return "notifications/log";
    }

    @PostMapping("/notifications/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','SECRETARY')")
    public String retry(@PathVariable Long id, RedirectAttributes ra) {
        service.retry(repo.findById(id).orElseThrow(() -> new NotFoundException("Notification")));
        flash.ok(ra, "msg.retryQueued");
        return "redirect:/notifications/log";
    }
}
