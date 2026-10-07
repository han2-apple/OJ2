package com.yupi.yuoj.model.dto.question;

import lombok.Data;

@Data
public class JudgeConfig {

    private Long timeLimit;

    private Long memoryLimit;

    private Long stackLimit;
}
