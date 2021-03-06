package com.example.algoproject.study.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class AddMember {

    @NotBlank
    private String memberName;

    @NotBlank
    private String studyId;
}
