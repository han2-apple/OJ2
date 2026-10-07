package com.yupi.yuoj.judge;

import com.yupi.yuoj.model.entity.QuestionSubmit;

public interface JudgeService {

    QuestionSubmit doJudge(long questionSubmitId);
}
