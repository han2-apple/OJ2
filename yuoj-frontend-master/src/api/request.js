import axios from 'axios';
import { Message } from '@arco-design/web-vue';

/**
 * 统一请求封装：
 *  - 后端所有接口挂在 /api 下（通过 Vite / Nginx 反向代理到后端服务）
 *  - 登录态基于 Cookie（Session），所以 withCredentials = true
 *  - 后端统一返回 { code, data, message }，code === 0 表示成功
 *  - 传入 { silent: true } 可关闭错误提示，由调用方自行处理（如“未登录”探测）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
});

function redirectToLogin() {
  const current = window.location.pathname + window.location.search;
  if (!current.startsWith('/user/login')) {
    const redirect = encodeURIComponent(current);
    window.location.replace(`/user/login?redirect=${redirect}`);
  }
}

request.interceptors.response.use(
  (response) => {
    const res = response.data;
    const silent = response.config?.silent;

    // 二进制等非统一包装响应直接放行
    if (!res || typeof res !== 'object' || !('code' in res)) {
      return res;
    }

    if (res.code === 0) {
      return res;
    }

    // 未登录 / 登录过期
    if (res.code === 40100) {
      if (!silent) {
        Message.error('未登录或登录已过期');
        redirectToLogin();
      }
      return Promise.reject(new Error(res.message || '未登录'));
    }

    if (!silent) {
      Message.error(res.message || '请求失败');
    }
    return Promise.reject(new Error(res.message || '请求失败'));
  },
  (error) => {
    const silent = error.config?.silent;
    if (!silent) {
      const msg =
        error.response?.data?.message ||
        (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请稍后重试');
      Message.error(msg);
    }
    return Promise.reject(error);
  }
);

export default request;
