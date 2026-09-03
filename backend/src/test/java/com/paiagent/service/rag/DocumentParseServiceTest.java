package com.paiagent.service.rag;

import com.paiagent.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParseServiceTest {
    @Test
    void rejectsSpoofedPdfExtension() {
        DocumentParseService service=new DocumentParseService(new RagProperties(),new TextCleaningService());
        MockMultipartFile file=new MockMultipartFile("file","attack.pdf","application/pdf","plain text".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->service.parse(file));
    }

    @Test
    void parsesAndCleansUtf8Text() throws Exception {
        DocumentParseService service=new DocumentParseService(new RagProperties(),new TextCleaningService());
        MockMultipartFile file=new MockMultipartFile("file","notes.txt","text/plain","第一行\r\n\r\n\r\n第二行".getBytes(StandardCharsets.UTF_8));
        var parsed=service.parse(file);
        assertEquals("第一行\n\n第二行",parsed.text());
        assertEquals(64,parsed.sha256().length());
    }

    @Test
    void rejectsFileBeforeReadingWhenDeclaredSizeExceedsLimit() {
        RagProperties properties=new RagProperties();properties.getLimits().setMaxFileBytes(3);
        DocumentParseService service=new DocumentParseService(properties,new TextCleaningService());
        MockMultipartFile file=new MockMultipartFile("file","notes.txt","text/plain","1234".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,()->service.parse(file));
    }
}
