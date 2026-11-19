package krd.parliament.vms.service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(EmailGateway.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String host;
    @Value("${app.mail.from:no-reply@localhost}")
    private String from;

    public EmailGateway(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public boolean isConfigured() {
        return host != null && !host.isBlank() && mailSender.getIfAvailable() != null;
    }

    public boolean send(String to, String subject, String body) {
        if (!isConfigured()) {
            log.info("[Email not configured] to={} subject={} : {}", to, subject, body);
            return false;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject(subject);
        m.setText(body);
        mailSender.getObject().send(m);
        return true;
    }
}
