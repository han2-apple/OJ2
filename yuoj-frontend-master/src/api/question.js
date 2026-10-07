import request from './request';

export function addQuestion(data) {
  return request.post('/question/add', data);
}

export function updateQuestion(data) {
  return request.post('/question/update', data);
}

export function deleteQuestion(data) {
  return request.post('/question/delete', data);
}

export function getQuestionById(id) {
  return request.get('/question/get', { params: { id } });
}

export function getQuestionVOById(id) {
  return request.get('/question/get/vo', { params: { id } });
}

export function listQuestionByPage(data) {
  return request.post('/question/list/page', data);
}

export function listQuestionVOByPage(data) {
  return request.post('/question/list/page/vo', data);
}

export function listMyQuestionVOByPage(data) {
  return request.post('/question/my/list/page/vo', data);
}

export function doQuestionSubmit(data) {
  return request.post('/question/question_submit/do', data);
}

export function listQuestionSubmitByPage(data) {
  return request.post('/question/question_submit/list/page', data);
}
