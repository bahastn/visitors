package krd.parliament.vms.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Stores uploaded documents on the local server disk (data stays inside parliament). */
@Service
public class StorageService {

    private static final Set<String> ALLOWED = Set.of("pdf", "jpg", "jpeg", "png", "gif", "webp", "doc", "docx",
            "xls", "xlsx", "txt", "tif", "tiff", "heic");

    @Value("${app.storage.attachments-dir}")
    private String dir;

    public String store(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED.contains(ext)) {
            throw new BusinessException("error.fileType", ext);
        }
        LocalDate d = LocalDate.now();
        String rel = d.getYear() + "/" + String.format("%02d", d.getMonthValue()) + "/" + UUID.randomUUID() + "." + ext;
        Path target = root().resolve(rel).normalize();
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return rel;
    }

    public Path resolve(String rel) {
        Path p = root().resolve(rel).normalize();
        if (!p.startsWith(root())) {
            throw new SecurityException("Invalid path");
        }
        return p;
    }

    public void delete(String rel) {
        try {
            Files.deleteIfExists(resolve(rel));
        } catch (IOException ignored) {
            // file already gone
        }
    }

    private Path root() {
        return Path.of(dir).toAbsolutePath().normalize();
    }
}
