<template>
  <a-config-provider :locale="zhCN">
    <a-layout class="app-layout">
      <a-layout-header class="layout-header">
        <div class="brand" @click="router.push('/')">
          <span class="brand-logo">&lt;/&gt;</span>
          <span class="brand-name">YUOJ 在线判题</span>
        </div>

        <a-menu
          class="nav-menu"
          mode="horizontal"
          :selected-keys="selectedKeys"
          @menu-item-click="(key) => router.push(key)"
        >
          <a-menu-item key="/">首页</a-menu-item>
          <a-menu-item key="/questions">题库</a-menu-item>
          <a-menu-item v-if="userStore.isLogin" key="/submit/list">
            提交记录
          </a-menu-item>
          <template v-if="userStore.isAdmin">
            <a-menu-item key="/admin/question">题目管理</a-menu-item>
            <a-menu-item key="/admin/user">用户管理</a-menu-item>
          </template>
        </a-menu>

        <div class="header-right">
          <template v-if="userStore.isLogin">
            <a-dropdown>
              <div class="user-entry">
                <a-avatar :size="30" :src="userStore.loginUser.userAvatar">
                  {{ avatarText }}
                </a-avatar>
                <span class="user-name">{{ userStore.loginUser.userName || '用户' }}</span>
              </div>
              <template #content>
                <a-doption @click="router.push('/user/center')">
                  <template #icon><icon-user /></template>
                  个人中心
                </a-doption>
                <a-doption @click="handleLogout">
                  <template #icon><icon-export /></template>
                  退出登录
                </a-doption>
              </template>
            </a-dropdown>
          </template>
          <template v-else>
            <a-space>
              <a-button @click="router.push('/user/login')">登录</a-button>
              <a-button type="primary" @click="router.push('/user/register')">
                注册
              </a-button>
            </a-space>
          </template>
        </div>
      </a-layout-header>

      <a-layout-content class="layout-content">
        <router-view />
      </a-layout-content>

      <a-layout-footer class="layout-footer">
        YUOJ 在线判题系统 · 前端基于 Vue3 + Arco Design
      </a-layout-footer>
    </a-layout>
  </a-config-provider>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn';
import { useUserStore } from '@/store/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const avatarText = computed(() => {
  const name = userStore.loginUser?.userName || 'U';
  return name.trim().slice(0, 1).toUpperCase();
});

const selectedKeys = computed(() => {
  const path = route.path;
  if (path === '/') return ['/'];
  if (path.startsWith('/questions') || path.startsWith('/question/')) {
    return ['/questions'];
  }
  if (path.startsWith('/submit/')) return ['/submit/list'];
  if (path.startsWith('/admin/question')) return ['/admin/question'];
  if (path.startsWith('/admin/user')) return ['/admin/user'];
  return [];
});

async function handleLogout() {
  await userStore.logout();
  Message.success('已退出登录');
  router.push('/');
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  height: 60px;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.brand {
  display: flex;
  align-items: center;
  cursor: pointer;
  margin-right: 24px;
  user-select: none;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-right: 8px;
  border-radius: 8px;
  background: linear-gradient(135deg, #165dff, #36cfc9);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  font-family: Consolas, monospace;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  color: #1d2129;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
}

.header-right {
  margin-left: auto;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  cursor: pointer;
}

.user-entry:hover {
  background: #f2f3f5;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: #1d2129;
}

.layout-content {
  min-height: calc(100vh - 60px - 56px);
}

.layout-footer {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #86909c;
  background: #fff;
}
</style>
