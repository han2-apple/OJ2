package com.yupi.yuoj.model.dto.questionsubmit;

import com.yupi.yuoj.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author han
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionSubmitQueryRequest extends PageRequest implements Serializable {

    private String language;

    private Integer status;

    private Long questionId;

    private Long userId;

    private static final long serialVersionUID = 1L;
}