package krd.parliament.vms.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import krd.parliament.vms.security.CryptoService;

/** Transparently encrypts a column at rest (used for ID / passport numbers). */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return CryptoService.get().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return CryptoService.get().decrypt(dbData);
    }
}
