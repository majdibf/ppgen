package com.pptxgenerator.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pptxgenerator.model.*;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class TemplateAnalyzerTest {

    @Test
    void testAnalyzeTemplate() throws Exception {
        TemplateAnalyzer analyzer = new TemplateAnalyzer();
        TemplateStructure structure = analyzer.analyze("template_1.pptx");

        assertNotNull(structure);
        assertNotNull(structure.getTheme());
        assertNotNull(structure.getTheme().getColors());
        assertNotNull(structure.getSlideDimensions());
        assertNotNull(structure.getLayouts());
        assertNotNull(structure.getStructuralElements());
        assertNotNull(structure.getMetadata());

        assertTrue(structure.getSlideDimensions().getWidth() > 0);
        assertTrue(structure.getSlideDimensions().getHeight() > 0);
        assertEquals("EMU", structure.getSlideDimensions().getUnit());

        assertEquals("1.0", structure.getMetadata().getAnalysisVersion());
        assertEquals("template_1.pptx", structure.getMetadata().getTemplateOriginalName());
        assertTrue(structure.getMetadata().getLayoutCount() > 0);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        File outputFile = new File("target/template-structure.json");
        outputFile.getParentFile().mkdirs();
        mapper.writeValue(outputFile, structure);
        assertTrue(outputFile.exists());

        String json = mapper.writeValueAsString(structure);
        TemplateStructure reloaded = mapper.readValue(json, TemplateStructure.class);
        assertEquals(structure.getLayouts().size(), reloaded.getLayouts().size());
    }

    @Test
    void testAnalyzeFromInputStream() throws Exception {
        TemplateAnalyzer analyzer = new TemplateAnalyzer();
        try (java.io.FileInputStream fis = new java.io.FileInputStream("template_1.pptx")) {
            TemplateStructure structure = analyzer.analyze(fis);
            assertNotNull(structure);
            assertNotNull(structure.getSlideDimensions());
            assertTrue(structure.getLayouts().size() > 0);
        }
    }
}
