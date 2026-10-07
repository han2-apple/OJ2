import { defineStore } from 'pinia';
import {
  getLoginUser,
  userLogin as loginApi,
  userLogout as logoutApi,
  userRegister as registerApi,
  updateMyUser as updateMyUserApi,
} from '@/api/user';

export const useUserStore = defineStore('user', {
  state: () => ({
    loginUser: null,
    authLoaded: false,
  }),
  getters: {
    isLogin: (state) => !!state.loginUser?.id,
    isAdmin: (state) => state.loginUser?.userRole === 'admin',
  },
  actions: {
    async fetchLoginUser() {
      try {
        const res = await getLoginUser();
        this.loginUser = res.data || null;
      } catch (e) {
        this.loginUser = null;
      } finally {
        this.authLoaded = true;
      }
    },
    async login(userAccount, userPassword) {
      const res = await loginApi({ userAccount, userPassword });
      this.loginUser = res.data || null;
      return this.loginUser;
    },
    async register(userAccount, userPassword, checkPassword) {
      const res = await registerApi({
        userAccount,
        userPassword,
        checkPassword,
      });
      return res.data;
    },
    async logout() {
      try {
        await logoutApi();
      } catch (e) {
        // 忽略登出接口异常，前端状态照常清理
      } finally {
        this.loginUser = null;
      }
    },
    async updateMyUser(data) {
      const res = await updateMyUserApi(data);
      this.loginUser = {
        ...(this.loginUser || {}),
        ...data,
      };
      return res.data;
    },
  },
});
