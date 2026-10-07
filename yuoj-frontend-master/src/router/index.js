import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/store/user';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/questions',
      name: 'questions',
      component: () => import('@/views/QuestionListView.vue'),
      meta: { title: '题库' },
    },
    {
      path: '/question/view/:id',
      name: 'questionView',
      component: () => import('@/views/DoQuestionView.vue'),
      meta: { title: '做题' },
    },
    {
      path: '/submit/list',
      name: 'submitList',
      component: () => import('@/views/SubmitListView.vue'),
      meta: { title: '提交记录', requiresLogin: true },
    },
    {
      path: '/user/login',
      name: 'userLogin',
      component: () => import('@/views/user/LoginView.vue'),
      meta: { title: '登录', guestOnly: true },
    },
    {
      path: '/user/register',
      name: 'userRegister',
      component: () => import('@/views/user/RegisterView.vue'),
      meta: { title: '注册', guestOnly: true },
    },
    {
      path: '/user/center',
      name: 'userCenter',
      component: () => import('@/views/user/UserCenterView.vue'),
      meta: { title: '个人中心', requiresLogin: true },
    },
    {
      path: '/admin/question',
      name: 'adminQuestion',
      component: () => import('@/views/admin/QuestionManageView.vue'),
      meta: { title: '题目管理', requiresLogin: true, requiresAdmin: true },
    },
    {
      path: '/admin/user',
      name: 'adminUser',
      component: () => import('@/views/admin/UserManageView.vue'),
      meta: { title: '用户管理', requiresLogin: true, requiresAdmin: true },
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/error/ForbiddenView.vue'),
      meta: { title: '无权限' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'notFound',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
});

router.beforeEach(async (to) => {
  const userStore = useUserStore();

  // 首次进入时根据会话 Cookie 拉取登录态
  if (!userStore.authLoaded) {
    await userStore.fetchLoginUser();
  }

  if (to.meta.requiresLogin && !userStore.isLogin) {
    return {
      path: '/user/login',
      query: { redirect: to.fullPath },
    };
  }
  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    return '/403';
  }
  if (to.meta.guestOnly && userStore.isLogin) {
    return '/';
  }
  return true;
});

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} - 在线判题系统`
    : '在线判题系统';
});

export default router;
