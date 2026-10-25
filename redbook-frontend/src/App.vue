<template>
  <router-view />
  <Toast />
</template>

<script setup lang="ts">
import Toast from './components/Toast.vue'
</script>

<!-- 全局重置与基础样式统一在 style.css（设计令牌）中维护 -->
