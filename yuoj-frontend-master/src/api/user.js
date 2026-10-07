import request from './request';

export function userLogin(data) {
  return request.post('/user/login', data);
}

export function userLogout() {
  return request.post('/user/logout');
}

export function userRegister(data) {
  return request.post('/user/register', data);
}

export function getLoginUser() {
  // silent：未登录时返回 40100 是正常情况，不弹错误、不跳登录页
  return request.get('/user/get/login', { silent: true });
}

export function getUserById(id) {
  return request.get('/user/get', { params: { id } });
}

export function getUserVOById(id) {
  return request.get('/user/get/vo', { params: { id } });
}

export function listUserByPage(data) {
  return request.post('/user/list/page', data);
}

export function listUserVOByPage(data) {
  return request.post('/user/list/page/vo', data);
}

export function addUser(data) {
  return request.post('/user/add', data);
}

export function updateUser(data) {
  return request.post('/user/update', data);
}

export function updateMyUser(data) {
  return request.post('/user/update/my', data);
}

export function deleteUser(data) {
  return request.post('/user/delete', data);
}
