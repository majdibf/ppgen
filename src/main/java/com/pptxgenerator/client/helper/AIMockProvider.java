package com.pptxgenerator.client.helper;

import com.pptxgenerator.client.GenerativeAiApi;
import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;

import java.util.List;

public class AIMockProvider implements GenerativeAiApi {

    @Override
    public TextResponseDto processGenerativeAI(TextRequestDto request) {
        String mockResponse = buildMockResponse(request);
        return new TextResponseDto(List.of(new TextResponseDto.TextCandidate(mockResponse)));
    }

    private String buildMockResponse(TextRequestDto request) {
        if (request.getUserPrompt() != null && request.getUserPrompt().toLowerCase().contains("plan")) {
            return """
                {
                  "title": "Présentation Mock",
                  "slide_count": 5,
                  "slides": [
                    {
                      "type": "cover",
                      "title": "Introduction",
                      "subtitle": "Sous-titre de la présentation"
                    },
                    {
                      "type": "content",
                      "title": "Point 1",
                      "bullet_points": ["Élément 1", "Élément 2", "Élément 3"]
                    },
                    {
                      "type": "content",
                      "title": "Point 2",
                      "bullet_points": ["Détail A", "Détail B"]
                    },
                    {
                      "type": "content",
                      "title": "Point 3",
                      "bullet_points": ["Aspect X", "Aspect Y", "Aspect Z"]
                    },
                    {
                      "type": "conclusion",
                      "title": "Conclusion",
                      "bullet_points": ["Résumé", "Prochaines étapes"]
                    }
                  ]
                }
                """;
        }

        return """
            {
              "content": "Réponse mock par défaut",
              "status": "success"
            }
            """;
    }
}
