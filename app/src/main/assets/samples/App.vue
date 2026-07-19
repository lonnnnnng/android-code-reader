<script setup lang="ts">
import { computed, ref } from 'vue';

const count = ref(1);
const label = computed(() => `Count: ${count.value}`);
</script>

<template><button @click="count++">{{ label }}</button></template>

<style scoped>button { color: #145c42; }</style>
