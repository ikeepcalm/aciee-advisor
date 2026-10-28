<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>

<style>
body {
  margin: 0;
  font-family: system-ui, sans-serif;
}

main {
  max-width: 28rem;
  margin: 0 auto;
  padding: 1rem;
}

.auth form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
</style>
