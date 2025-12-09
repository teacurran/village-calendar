<script setup lang="ts">
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/authStore";
import Button from "primevue/button";
import Card from "primevue/card";

const router = useRouter();
const authStore = useAuthStore();

// Redirect if already authenticated
onMounted(() => {
  if (authStore.isAuthenticated) {
    const returnTo = sessionStorage.getItem("auth_return_to");
    if (returnTo) {
      sessionStorage.removeItem("auth_return_to");
      router.push(returnTo);
    } else {
      router.push("/");
    }
  }
});

// Login with OAuth provider
const loginWithProvider = (provider: "google" | "facebook") => {
  authStore.initiateLogin(provider);
};
</script>

<template>
  <div class="login-container">
    <Card class="login-card">
      <template #title>
        <div class="text-center">
          <i class="pi pi-calendar text-4xl text-primary mb-3"></i>
          <h2>Sign In</h2>
        </div>
      </template>
      <template #content>
        <p class="text-center text-gray-600 mb-4">
          Sign in to access admin tools and manage your account.
        </p>
        <div class="login-buttons">
          <Button
            label="Sign in with Google"
            icon="pi pi-google"
            class="w-full mb-3"
            @click="loginWithProvider('google')"
          />
          <Button
            label="Sign in with Facebook"
            icon="pi pi-facebook"
            severity="info"
            class="w-full"
            @click="loginWithProvider('facebook')"
          />
        </div>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  padding: 2rem;
}

.login-card {
  width: 100%;
  max-width: 400px;
}

.login-buttons {
  display: flex;
  flex-direction: column;
}
</style>
