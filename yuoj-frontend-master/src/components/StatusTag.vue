<template>
  <a-tag :color="meta.color">{{ meta.text }}</a-tag>
</template>

<script setup>
import { computed } from 'vue';
import { SUBMIT_STATUS_MAP } from '@/constants/question';

const props = defineProps({
  status: {
    type: [Number, String],
    required: true,
  },
});

const meta = computed(
  () => SUBMIT_STATUS_MAP[props.status] || { text: '未知', color: 'gray' }
);
</script>
