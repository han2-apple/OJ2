<template>
  <div class="error-page">
    <a-result status="403" title="403" subtitle="抱歉，你没有权限访问该页面">
      <template #extra>
        <a-space>
          <a-button @click="router.back()">返回上一页</a-button>
          <a-button type="primary" @click="router.push('/')">回到首页</a-button>
        </a-space>
      </template>
    </a-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 116px);
}
</style>
