package com.paiagent.service.rag;

import com.paiagent.config.RagProperties;
import org.apache.tika.Tika;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentParseService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "markdown");
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain", "text/markdown", "text/x-markdown");
    private final RagProperties properties;
    private final TextCleaningService cleaningService;
    private final Tika tika = new Tika();

    public DocumentParseService(RagProperties properties, TextCleaningService cleaningService) {
        this.properties = properties;
        this.cleaningService = cleaningService;
    }

    public ParsedDocument parse(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > properties.getLimits().getMaxFileBytes()) throw new IllegalArgumentException("文件超过允许大小");
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extension(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw new IllegalArgumentException("仅支持 PDF、DOCX、TXT、MD 文件");
        byte[] bytes = file.getBytes();
        String mediaType = tika.detect(bytes, fileName).toLowerCase(Locale.ROOT);
        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) throw new IllegalArgumentException("文件真实类型不受支持: " + mediaType);
        if (!matchesExtension(extension, mediaType)) throw new IllegalArgumentException("文件扩展名与真实类型不一致");
        String cleaned = cleaningService.clean(parse(bytes));
        validateText(cleaned);
        return new ParsedDocument(fileName, mediaType, sha256(bytes), cleaned, bytes);
    }

    public String cleanText(String text) {
        String cleaned = cleaningService.clean(text);
        validateText(cleaned);
        return cleaned;
    }

    private String parse(byte[] bytes) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler(properties.getLimits().getMaxTextChars());
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        context.set(EmbeddedDocumentExtractor.class, new RejectEmbeddedExtractor());
        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setExtractInlineImages(false);
        pdf.setSortByPosition(true);
        context.set(PDFParserConfig.class, pdf);
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            parser.parse(input, handler, new Metadata(), context);
        }
        return handler.toString();
    }

    private void validateText(String text) {
        if (text.isBlank()) throw new IllegalArgumentException("文档没有可索引文本");
        if (text.length() > properties.getLimits().getMaxTextChars()) throw new IllegalArgumentException("解析文本超过字符上限");
    }

    private String sanitizeFileName(String raw) {
        String name = raw == null ? "uploaded.txt" : raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return name.isBlank() ? "uploaded.txt" : name;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean matchesExtension(String extension, String mediaType) {
        return switch (extension) {
            case "pdf" -> "application/pdf".equals(mediaType);
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mediaType);
            case "txt", "md", "markdown" -> mediaType.startsWith("text/");
            default -> false;
        };
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public record ParsedDocument(String fileName, String mediaType, String sha256, String text, byte[] bytes) {}

    private static final class RejectEmbeddedExtractor implements EmbeddedDocumentExtractor {
        public boolean shouldParseEmbedded(Metadata metadata) { return false; }
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws IOException { }
    }
}
