package tech.cuia.notifyhub.infrastructure.channel.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.InputStream;
import java.util.Arrays;

/**
 * Substitui o JavaMailSender real por um stub que apenas loga as mensagens.
 * Ativo apenas no perfil "dev" — em "prod", o Spring configura o sender real via application-prod.yml.
 */
@Configuration
@Profile("dev")
class DevMailStubConfig {

    @Bean
    @Primary
    JavaMailSender devMailSender() {
        return new LoggingMailSender();
    }

    static class LoggingMailSender extends JavaMailSenderImpl {

        private static final Logger log = LoggerFactory.getLogger("notify-hub.mail.stub");

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            logMime(mimeMessage);
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            Arrays.stream(mimeMessages).forEach(this::logMime);
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            log.info("[STUB EMAIL] to={} subject=\"{}\"",
                    Arrays.toString(simpleMessage.getTo()), simpleMessage.getSubject());
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            Arrays.stream(simpleMessages).forEach(this::send);
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            return createMimeMessage();
        }

        private void logMime(MimeMessage message) {
            try {
                log.info("[STUB EMAIL] to={} subject=\"{}\"",
                        Arrays.toString(message.getAllRecipients()), message.getSubject());
            } catch (MessagingException e) {
                log.warn("[STUB EMAIL] Could not read message details: {}", e.getMessage());
            }
        }
    }
}
