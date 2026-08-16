package com.pptxgenerator.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warning {
    private String code;
    private String message;
    private List<Integer> affectedSlides;
}
