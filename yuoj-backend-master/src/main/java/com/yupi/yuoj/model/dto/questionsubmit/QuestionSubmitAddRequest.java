package com.yupi.yuoj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serializable;

/**
 * @author han
 */
@Data
public class QuestionSubmitAddRequest implements Serializable {

    private String language;

    private String code;

    private Long questionId;

    private static final long serialVersionUID = 1L;
}