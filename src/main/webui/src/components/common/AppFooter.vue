<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="footer-section">
        <div class="footer-brand">
          <i class="pi pi-calendar text-xl mr-2"></i>
          <span class="brand-name">Village Calendar</span>
        </div>
        <p class="footer-tagline">
          Custom yearly wall calendars with holidays, moon phases, and lunar
          illumination
        </p>
      </div>

      <div class="footer-section">
        <h3 class="footer-heading">Quick Links</h3>
        <ul class="footer-links">
          <li>
            <router-link to="/" class="footer-link">Home</router-link>
          </li>
          <li>
            <router-link to="/" class="footer-link">Templates</router-link>
          </li>
          <li v-if="authStore.isAuthenticated">
            <router-link to="/dashboard" class="footer-link"
              >My Orders</router-link
            >
          </li>
        </ul>
      </div>

      <div class="footer-section">
        <h3 class="footer-heading">Support</h3>
        <ul class="footer-links">
          <li>
            <a href="/pages/shipping" class="footer-link">Shipping Info</a>
          </li>
        </ul>
      </div>

      <div class="footer-section">
        <h3 class="footer-heading">Legal</h3>
        <ul class="footer-links">
          <li>
            <a href="/pages/privacy" class="footer-link">Privacy Policy</a>
          </li>
          <li>
            <a href="/pages/terms" class="footer-link">Terms of Service</a>
          </li>
          <li>
            <a href="/pages/refund" class="footer-link">Refund Policy</a>
          </li>
        </ul>
      </div>
    </div>

    <div class="footer-bottom">
      <p class="copyright">
        &copy; {{ currentYear }} Village Compute. All rights reserved.
      </p>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useAuthStore } from "@/stores/authStore";

const authStore = useAuthStore();

// Current year for copyright
const currentYear = computed(() => new Date().getFullYear());
</script>

<style scoped>
.app-footer {
  background: #1f2937;
  color: #e5e7eb;
  margin-top: auto;
}

.footer-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 3rem 1rem 2rem;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 2rem;
}

.footer-section {
  display: flex;
  flex-direction: column;
}

.footer-brand {
  display: flex;
  align-items: center;
  color: white;
  font-weight: 700;
  font-size: 1.25rem;
  margin-bottom: 0.75rem;
}

.brand-name {
  white-space: nowrap;
}

.footer-tagline {
  color: #9ca3af;
  font-size: 0.875rem;
  line-height: 1.5;
  max-width: 250px;
}

.footer-heading {
  color: white;
  font-weight: 600;
  font-size: 1rem;
  margin-bottom: 1rem;
}

.footer-links {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.footer-link {
  color: #9ca3af;
  text-decoration: none;
  font-size: 0.875rem;
  transition: color 0.2s;
  cursor: pointer;
}

.footer-link:hover {
  color: white;
}

.footer-bottom {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1.5rem 1rem;
  border-top: 1px solid #374151;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.copyright {
  color: #9ca3af;
  font-size: 0.875rem;
  margin: 0;
}

/* Responsive styles */
@media (max-width: 768px) {
  .footer-content {
    grid-template-columns: repeat(2, 1fr);
  }

  .footer-bottom {
    flex-direction: column;
    text-align: center;
  }
}

@media (max-width: 480px) {
  .footer-content {
    grid-template-columns: 1fr;
  }
}
</style>
