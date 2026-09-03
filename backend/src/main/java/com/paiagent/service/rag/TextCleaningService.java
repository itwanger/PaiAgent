package com.paiagent.service.rag;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class TextCleaningService {
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");
    private static final Pattern IMAGE_LINE = Pattern.compile("(?im)^\\s*image\\d+\\.(png|jpe?g|gif|bmp|webp)\\s*$");
    private static final Pattern FILE_URL = Pattern.compile("(?i)file:(//)?\\S+");

    public String clean(String value) {
        if (value == null || value.isBlank()) return "";
        String text = value.replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL.matcher(text).replaceAll("");
        text = IMAGE_LINE.matcher(text).replaceAll("");
        text = FILE_URL.matcher(text).replaceAll("");
        text = text.replaceAll("(?m)[ \\t]+$", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }
}
