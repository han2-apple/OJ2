<template>
  <div class="page-container">
    <div class="hero">
      <h1 class="hero-title">在线刷题 · 实时判题</h1>
      <p class="hero-desc">
        支持 Java / C++ / Go，覆盖从入门到进阶的算法题目。
        提交代码后即可看到实时判题结果，助你稳步提升编程能力。
      </p>
      <a-space size="large">
        <a-button type="primary" size="large" @click="router.push('/questions')">
          开始刷题
        </a-button>
        <a-button
          v-if="!userStore.isLogin"
          size="large"
          @click="router.push('/user/register')"
        >
          免费注册
        </a-button>
        <a-button v-else size="large" @click="router.push('/submit/list')">
          我的提交记录
        </a-button>
      </a-space>
    </div>

    <a-card class="recent-card" :bordered="false">
      <template #title>
        <div class="section-title">最近题目</div>
      </template>
      <template #extra>
        <a-link @click="router.push('/questions')">查看全部</a-link>
      </template>

      <a-spin :loading="loading" style="display: block">
        <a-empty
          v-if="!loading && recentList.length === 0"
          description="暂无题目，或后端服务暂不可用"
        />
        <a-row v-else :gutter="[16, 16]">
          <a-col v-for="item in recentList" :key="item.id" :xs="24" :sm="12" :lg="8">
            <a-card
              class="question-card"
              hoverable
              :bordered="false"
              @click="router.push(`/question/view/${item.id}`)"
            >
              <div class="q-title">{{ item.title }}</div>
              <div class="q-tags">
                <a-tag
                  v-for="tag in item.tags || []"
                  :key="tag"
                  :color="tagColor(tag)"
                >
                  {{ tag }}
                </a-tag>
              </div>
              <div class="q-stats">
                <span>通过率 {{ calcPassRate(item.acceptedNum, item.submitNum) }}</span>
                <span>提交 {{ item.submitNum || 0 }}</span>
              </div>
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listQuestionVOByPage } from '@/api/question';
import { useUserStore } from '@/store/user';
import { calcPassRate } from '@/utils';

const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const recentList = ref([]);

const TAG_COLORS = {
  简单: 'green',
  中等: 'orange',
  困难: 'red',
};

function tagColor(tag) {
  return TAG_COLORS[tag] || 'arcoblue';
}

async function loadRecent() {
  loading.value = true;
  try {
    const res = await listQuestionVOByPage({
      current: 1,
      pageSize: 6,
      sortField: 'createTime',
      sortOrder: 'descend',
    });
    recentList.value = res.data?.records || [];
  } catch (e) {
    recentList.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(loadRecent);
</script>

<style scoped>
.hero {
  text-align: center;
  padding: 64px 16px 40px;
  background: linear-gradient(160deg, #f2f7ff 0%, #ffffff 55%, #effbff 100%);
  border-radius: 16px;
  margin-bottom: 24px;
}

.hero-title {
  font-size: 36px;
  margin: 0 0 16px;
  color: #0e1a33;
}

.hero-desc {
  max-width: 640px;
  margin: 0 auto 28px;
  color: #4e5969;
  font-size: 16px;
  line-height: 1.7;
}

.recent-card {
  border-radius: 12px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
}

.question-card {
  border-radius: 10px;
}

.q-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.q-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 26px;
  margin-bottom: 12px;
}

.q-stats {
  display: flex;
  justify-content: space-between;
  color: #86909c;
  font-size: 13px;
}
</style>
