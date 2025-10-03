<template>
  <div id="app">
    <Toast />
    <ConfirmDialog />
    <RouterView />
  </div>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
</script>

<style scoped>
#app {
  min-height: 100vh;
}
</style>
