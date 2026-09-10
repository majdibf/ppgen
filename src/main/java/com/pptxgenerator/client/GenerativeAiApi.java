package com.pptxgenerator.client;

import com.pptxgenerator.client.dto.TextRequestDto;
import com.pptxgenerator.client.dto.TextResponseDto;

public interface GenerativeAiApi {

    TextResponseDto processGenerateAI(TextRequestDto request);
}
