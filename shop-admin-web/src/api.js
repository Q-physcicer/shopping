import axios from 'axios';

/**
 * 统一请求封装：
 * - JWT 存 localStorage（Authorization Bearer），同时后端 Set-Cookie XM_TOKEN（SSE 需要 cookie，EventSource 无法带 header）
 * - Result{code,msg,data} 自动解包；HTTP 401 → 踢回登录页
 */
const api = axios.create({ baseURL: '/api', timeout: 30000 });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('ADMIN_JWT');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => {
    // Result 包装：code=1 成功；成功 msg 附到 __msg（页面可取后端 reunited 文案，如"重建成功，共 N 条"）
    if (res.data && typeof res.data.code === 'number') {
      if (res.data.code === 1) {
        if (res.data.data !== null && typeof res.data.data === 'object') {
          try { res.data.data.__msg = res.data.msg; } catch { /* 只读对象忽略 */ }
        }
        return res.data.data;   // 直接返回 data，页面代码不用层层解包
      }
      return Promise.reject(new Error(res.data.msg || '操作失败'));
    }
    return res.data;
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('ADMIN_JWT');
      localStorage.removeItem('ADMIN_USER');
      if (window.location.pathname !== '/login') {
        // 带 redirect：登录后回跳原页面（原直接踢首页，筛选/页码全丢）
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`;
      }
      return Promise.reject(new Error((err.response.data && err.response.data.msg) || '请先登录'));
    }
    return Promise.reject(new Error((err.response && err.response.data && err.response.data.msg) || err.message || '网络异常'));
  }
);

/** 登录（成功后存 JWT + 用户信息；后端同时会 Set-Cookie XM_TOKEN 用于 SSE） */
export async function login(username, password) {
  const res = await axios.post('/api/user/login', { username, password });
  const cookieJwt = document.cookie.match(/XM_TOKEN=([^;]+)/);
  if (res.data.code === 1) {
    localStorage.setItem('ADMIN_JWT', cookieJwt ? cookieJwt[1] : '');
    localStorage.setItem('ADMIN_USER', JSON.stringify(res.data.data || {}));
    return res.data.data;
  }
  throw new Error(res.data.msg || '登录失败');
}

export function logout() {
  axios.post('/api/user/logout').catch(() => {});
  localStorage.removeItem('ADMIN_JWT');
  localStorage.removeItem('ADMIN_USER');
  window.location.href = '/login';
}

export function currentUser() {
  try {
    return JSON.parse(localStorage.getItem('ADMIN_USER') || 'null');
  } catch {
    return null;
  }
}

export default api;