package com.yupi.yuoj.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

@TableName(value ="question_submit")
@Data
public class QuestionSubmit implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String language;

    private String code;

    private String judgeInfo;

    private Integer status;

    private Long questionId;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}