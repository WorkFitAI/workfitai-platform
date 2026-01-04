package org.workfitai.cvservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParsedCvData {
    private String headline;
    private List<String> summary;

    private List<String> skills = new ArrayList<>();
    private List<String> projects = new ArrayList<>();
    private List<String> experience = new ArrayList<>();
    private List<String> education = new ArrayList<>();
    private List<String> languages = new ArrayList<>();
}
