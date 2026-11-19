package krd.parliament.vms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Daily PostgreSQL backups with pg_dump, keeping the most recent N files. */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    public record BackupFile(String name, long sizeBytes, LocalDateTime created) {
    }

    @Value("${app.backup.enabled:true}")
    private boolean enabled;
    @Value("${app.backup.dir}")
    private String dir;
    @Value("${app.backup.pg-dump-path:pg_dump}")
    private String pgDump;
    @Value("${app.backup.keep:14}")
    private int keep;
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    @Value("${spring.datasource.username}")
    private String dbUser;
    @Value("${spring.datasource.password}")
    private String dbPassword;

    private final AuditService audit;

    public BackupService(AuditService audit) {
        this.audit = audit;
    }

    @Scheduled(cron = "${app.backup.cron}", zone = "${app.time-zone}")
    public void scheduledBackup() {
        if (enabled) {
            try {
                runBackup();
            } catch (Exception e) {
                log.error("Scheduled backup failed: {}", e.getMessage());
            }
        }
    }

    public String runBackup() throws IOException, InterruptedException {
        Path folder = Path.of(dir).toAbsolutePath();
        Files.createDirectories(folder);
        String name = "vms-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".dump";
        Path target = folder.resolve(name);
        // jdbc:postgresql://host:port/db
        String rest = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
        String hostPort = rest.substring(0, rest.indexOf('/'));
        String db = rest.substring(rest.indexOf('/') + 1).split("\\?")[0];
        String host = hostPort.contains(":") ? hostPort.split(":")[0] : hostPort;
        String port = hostPort.contains(":") ? hostPort.split(":")[1] : "5432";
        ProcessBuilder pb = new ProcessBuilder(pgDump, "-h", host, "-p", port, "-U", dbUser, "-F", "c", "-f",
                target.toString(), db);
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(10, TimeUnit.MINUTES) || p.exitValue() != 0) {
            Files.deleteIfExists(target);
            throw new IOException("pg_dump failed: " + output);
        }
        cleanup(folder);
        audit.log(AuditService.SYSTEM, null, "BACKUP_CREATED", name);
        log.info("Backup created {}", target);
        return name;
    }

    public List<BackupFile> list() {
        Path folder = Path.of(dir).toAbsolutePath();
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".dump"))
                    .map(p -> {
                        File f = p.toFile();
                        return new BackupFile(f.getName(), f.length(),
                                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(f.lastModified()), java.time.ZoneId.systemDefault()));
                    })
                    .sorted(Comparator.comparing(BackupFile::created).reversed()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Path file(String name) {
        if (!name.matches("vms-\\d{8}-\\d{6}\\.dump")) {
            throw new SecurityException("Invalid backup name");
        }
        return Path.of(dir).toAbsolutePath().resolve(name);
    }

    private void cleanup(Path folder) throws IOException {
        List<BackupFile> all = list();
        for (int i = keep; i < all.size(); i++) {
            Files.deleteIfExists(folder.resolve(all.get(i).name()));
        }
    }
}
