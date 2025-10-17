<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="footer-section">
        <div class="footer-brand">
          <i class="pi pi-calendar text-xl mr-2"></i>
          <span class="brand-name">Village Calendar</span>
        </div>
        <p class="footer-tagline">
          Create beautiful, personalized calendars for any occasion
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
            <a href="#" class="footer-link" @click.prevent="handleContactClick"
              >Contact Us</a
            >
          </li>
          <li>
            <a href="#" class="footer-link" @click.prevent="handleFaqClick"
              >FAQ</a
            >
          </li>
          <li>
            <a href="#" class="footer-link" @click.prevent="handleShippingClick"
              >Shipping Info</a
            >
          </li>
        </ul>
      </div>

      <div class="footer-section">
        <h3 class="footer-heading">Legal</h3>
        <ul class="footer-links">
          <li>
            <a href="#" class="footer-link" @click.prevent="handlePrivacyClick"
              >Privacy Policy</a
            >
          </li>
          <li>
            <a href="#" class="footer-link" @click.prevent="handleTermsClick"
              >Terms of Service</a
            >
          </li>
          <li>
            <a href="#" class="footer-link" @click.prevent="handleRefundClick"
              >Refund Policy</a
            >
          </li>
        </ul>
      </div>
    </div>

    <div class="footer-bottom">
      <p class="copyright">
        &copy; {{ currentYear }} Village Calendar. All rights reserved.
      </p>
      <div class="social-links">
        <a
          href="#"
          class="social-link"
          aria-label="Facebook"
          @click.prevent="handleSocialClick('facebook')"
        >
          <i class="pi pi-facebook"></i>
        </a>
        <a
          href="#"
          class="social-link"
          aria-label="Twitter"
          @click.prevent="handleSocialClick('twitter')"
        >
          <i class="pi pi-twitter"></i>
        </a>
        <a
          href="#"
          class="social-link"
          aria-label="Instagram"
          @click.prevent="handleSocialClick('instagram')"
        >
          <i class="pi pi-instagram"></i>
        </a>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useAuthStore } from "@/stores/authStore";

const authStore = useAuthStore();

// Current year for copyright
const currentYear = computed(() => new Date().getFullYear());

// Placeholder click handlers (can be implemented later)
const handleContactClick = () => {
  console.log("Contact Us clicked");
};

const handleFaqClick = () => {
  console.log("FAQ clicked");
};

const handleShippingClick = () => {
  console.log("Shipping Info clicked");
};

const handlePrivacyClick = () => {
  console.log("Privacy Policy clicked");
};

const handleTermsClick = () => {
  console.log("Terms of Service clicked");
};

const handleRefundClick = () => {
  console.log("Refund Policy clicked");
};

const handleSocialClick = (platform: string) => {
  console.log(`${platform} social link clicked`);
};
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

.social-links {
  display: flex;
  gap: 1rem;
}

.social-link {
  color: #9ca3af;
  font-size: 1.25rem;
  transition: color 0.2s;
  text-decoration: none;
}

.social-link:hover {
  color: white;
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
