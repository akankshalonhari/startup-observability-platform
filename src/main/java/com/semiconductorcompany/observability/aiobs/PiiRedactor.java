package com.semiconductorcompany.observability.aiobs;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** PII redaction on agent output (O-3). Deliberately simple: emails and international phone numbers. */
@Component
public class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("\\+\\d[\\d ()-]{7,}\\d");

    public String redact(String text) {
        if (text == null) {
            return null;
        }
        return PHONE.matcher(EMAIL.matcher(text).replaceAll("[email]")).replaceAll("[phone]");
    }
}
