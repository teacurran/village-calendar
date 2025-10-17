<template>
  <div class="calendar-marketing">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content-container">
        <div class="hero-content">
          <h1 class="hero-title">Custom Calendar Printing</h1>
          <p class="hero-subtitle">
            Create beautiful, personalized calendars with moon phases, holidays,
            and custom events. Perfect for gifts, planning, or decoration.
          </p>
          <div class="hero-buttons">
            <Button
              label="Create Your Calendar"
              icon="pi pi-plus"
              size="large"
              @click="router.push('/printing/calendar-generator')"
            />
            <Button
              label="Browse Templates"
              icon="pi pi-eye"
              size="large"
              @click="scrollToTemplates"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section py-16 bg-white">
      <div class="container mx-auto px-4">
        <h2 class="text-3xl font-bold text-center mb-12">
          Why Choose Our Calendars?
        </h2>
        <div class="grid md:grid-cols-3 gap-8">
          <div class="text-center">
            <div class="text-4xl mb-4">🌙</div>
            <h3 class="text-xl font-semibold mb-2">Moon Phases</h3>
            <p class="text-gray-600">
              Accurate moon phase calculations for any location on Earth
            </p>
          </div>
          <div class="text-center">
            <div class="text-4xl mb-4">🎨</div>
            <h3 class="text-xl font-semibold mb-2">Customizable Design</h3>
            <p class="text-gray-600">
              Choose themes, colors, and layouts to match your style
            </p>
          </div>
          <div class="text-center">
            <div class="text-4xl mb-4">📅</div>
            <h3 class="text-xl font-semibold mb-2">Personal Events</h3>
            <p class="text-gray-600">
              Add birthdays, anniversaries, and special occasions with emojis
            </p>
          </div>
        </div>
      </div>
    </section>

    <!-- Templates Section -->
    <section ref="templatesSection" class="templates-section py-16 bg-gray-50">
      <div class="container mx-auto px-4">
        <h2 class="text-3xl font-bold text-center mb-4">
          Start with a Template
        </h2>
        <p class="text-center text-gray-600 mb-12">
          Choose from our professionally designed templates or create your own
          from scratch
        </p>

        <!-- Loading state -->
        <div v-if="loading" class="flex justify-center py-12">
          <ProgressSpinner />
        </div>

        <!-- Templates Grid -->
        <div
          v-else-if="templates.length > 0"
          class="grid md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6"
        >
          <Card
            v-for="template in templates"
            :key="template.id"
            class="template-card hover:shadow-lg transition-shadow"
          >
            <template #header>
              <div class="relative h-48 bg-gray-200 overflow-hidden">
                <!-- Show preview if available -->
                <div
                  v-if="templatePreviews[template.id]"
                  class="w-full h-full flex items-center justify-center p-2 bg-white"
                  v-html="templatePreviews[template.id]"
                ></div>
                <!-- Show loading spinner while generating -->
                <div
                  v-else-if="
                    isGeneratingPreviews &&
                    previewGenerationQueue.some((t) => t.id === template.id)
                  "
                  class="w-full h-full flex items-center justify-center"
                >
                  <ProgressSpinner style="width: 40px; height: 40px" />
                </div>
                <!-- Show thumbnail URL if provided -->
                <img
                  v-else-if="template.thumbnailUrl"
                  :src="template.thumbnailUrl"
                  :alt="template.name"
                  class="w-full h-full object-cover"
                />
                <!-- Default calendar icon -->
                <div
                  v-else
                  class="w-full h-full flex items-center justify-center"
                >
                  <i class="pi pi-calendar text-4xl text-gray-400"></i>
                </div>
                <Tag
                  v-if="template.isFeatured"
                  value="Featured"
                  severity="warning"
                  class="absolute top-2 right-2"
                />
              </div>
            </template>
            <template #title>
              {{ template.name }}
            </template>
            <template #content>
              <p class="text-gray-600 text-sm mb-4">
                {{ template.description }}
              </p>
              <div
                v-if="template.basePrice"
                class="text-lg font-semibold text-green-600 mb-4"
              >
                ${{ template.basePrice }}
              </div>
            </template>
            <template #footer>
              <div class="flex gap-2">
                <Button
                  label="Preview"
                  icon="pi pi-eye"
                  outlined
                  size="small"
                  class="flex-1"
                  @click="previewTemplate(template)"
                />
                <Button
                  label="Customize"
                  icon="pi pi-pencil"
                  size="small"
                  class="flex-1"
                  @click="customizeTemplate(template)"
                />
              </div>
            </template>
          </Card>

          <!-- Create from Scratch Card -->
          <Card
            class="template-card hover:shadow-lg transition-shadow border-2 border-dashed border-gray-300"
          >
            <template #header>
              <div
                class="h-48 bg-gradient-to-br from-blue-50 to-green-50 flex items-center justify-center"
              >
                <i class="pi pi-plus-circle text-6xl text-gray-400"></i>
              </div>
            </template>
            <template #title> Create from Scratch </template>
            <template #content>
              <p class="text-gray-600 text-sm mb-4">
                Start with a blank calendar and customize every detail
              </p>
            </template>
            <template #footer>
              <Button
                label="Start Creating"
                icon="pi pi-plus"
                class="w-full"
                @click="router.push('/printing/calendar-generator')"
              />
            </template>
          </Card>
        </div>

        <!-- No templates state -->
        <div v-else class="text-center py-12">
          <i class="pi pi-inbox text-6xl text-gray-300 mb-4"></i>
          <p class="text-gray-500">No templates available yet</p>
          <Button
            label="Create Your Own Calendar"
            icon="pi pi-plus"
            class="mt-4"
            @click="router.push('/printing/calendar-generator')"
          />
        </div>
      </div>
    </section>

    <!-- Gallery Section -->
    <section class="gallery-section py-16 bg-white">
      <div class="container mx-auto px-4">
        <h2 class="text-3xl font-bold text-center mb-12">Customer Creations</h2>
        <div class="grid md:grid-cols-3 gap-6">
          <!-- Placeholder for customer gallery images -->
          <div class="aspect-square bg-gray-200 rounded-lg"></div>
          <div class="aspect-square bg-gray-200 rounded-lg"></div>
          <div class="aspect-square bg-gray-200 rounded-lg"></div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="container mx-auto px-4 text-center">
        <h2 class="text-3xl font-bold mb-4">Ready to Create Your Calendar?</h2>
        <p class="text-xl mb-8">Design your perfect calendar in minutes</p>
        <Button
          label="Get Started Now"
          icon="pi pi-arrow-right"
          size="large"
          severity="secondary"
          raised
          @click="router.push('/printing/calendar-generator')"
        />
      </div>
    </section>

    <!-- Preview Modal -->
    <Dialog
      v-model:visible="showPreview"
      modal
      :header="selectedTemplate?.name"
      :style="{ width: '90vw', maxWidth: '1200px' }"
      :maximizable="true"
    >
      <div v-if="selectedTemplate" class="preview-container">
        <div v-if="previewLoading" class="flex justify-center py-8">
          <ProgressSpinner />
        </div>
        <div
          v-else-if="previewSvg"
          class="calendar-preview"
          v-html="previewSvg"
        ></div>
        <div v-else class="text-center py-8 text-gray-500">
          Preview not available
        </div>
      </div>
      <template #footer>
        <Button label="Close" text @click="showPreview = false" />
        <Button
          label="Customize This Template"
          icon="pi pi-pencil"
          @click="customizeTemplate(selectedTemplate)"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useToast } from "primevue/usetoast";
import Card from "primevue/card";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import ProgressSpinner from "primevue/progressspinner";
import Tag from "primevue/tag";

const router = useRouter();
const toast = useToast();

// State
const loading = ref(false);
const templates = ref([]);
const showPreview = ref(false);
const selectedTemplate = ref(null);
const previewSvg = ref("");
const previewLoading = ref(false);
const templatesSection = ref(null);
const templatePreviews = ref({});
const previewGenerationQueue = ref([]);
const isGeneratingPreviews = ref(false);

// Load templates
const loadTemplates = async () => {
  loading.value = true;
  try {
    const response = await fetch("/api/calendar-templates");
    if (response.ok) {
      templates.value = await response.json();

      // If no templates exist, use sample data for demonstration
      if (templates.value.length === 0) {
        templates.value = getSampleTemplates();
      }

      // Generate previews for templates after loading
      generateTemplatePreviews();
    } else {
      throw new Error("Failed to load templates");
    }
  } catch (error) {
    console.error("Error loading templates:", error);
    // Use sample templates for demonstration
    templates.value = getSampleTemplates();
    generateTemplatePreviews();
  } finally {
    loading.value = false;
  }
};

// Get sample templates for demonstration
const getSampleTemplates = () => {
  const currentYear = new Date().getFullYear();
  return [
    {
      id: "sample-1",
      name: "Classic Monthly",
      description: "Traditional monthly calendar with holidays and moon phases",
      isFeatured: true,
      basePrice: 19.99,
      configuration: {
        year: currentYear,
        calendarType: "gregorian",
        startMonth: 1,
        endMonth: 12,
        showMoonPhases: true,
        showWeekNumbers: false,
        weekStartsOn: 0,
        theme: "modern",
        showDayNames: true,
        showDayNumbers: true,
        showGrid: true,
        compactMode: false,
      },
    },
    {
      id: "sample-2",
      name: "Hebrew Calendar",
      description: "Hebrew calendar with Jewish holidays and Shabbat times",
      isFeatured: false,
      basePrice: 24.99,
      configuration: {
        year: currentYear,
        calendarType: "hebrew",
        startMonth: 1,
        endMonth: 12,
        showMoonPhases: false,
        showWeekNumbers: false,
        weekStartsOn: 0,
        theme: "classic",
        showDayNames: true,
        showDayNumbers: true,
        showGrid: true,
        compactMode: false,
      },
    },
    {
      id: "sample-3",
      name: "Nature Photography",
      description:
        "Beautiful calendar with nature photos and environmental dates",
      isFeatured: true,
      basePrice: 29.99,
      configuration: {
        year: currentYear,
        calendarType: "gregorian",
        startMonth: 1,
        endMonth: 12,
        showMoonPhases: true,
        showWeekNumbers: false,
        weekStartsOn: 1,
        theme: "nature",
        showDayNames: true,
        showDayNumbers: true,
        showGrid: true,
        compactMode: false,
      },
    },
    {
      id: "sample-4",
      name: "Academic Year",
      description:
        "Perfect for students and teachers, runs from August to July",
      isFeatured: false,
      basePrice: 22.99,
      configuration: {
        year: currentYear,
        calendarType: "gregorian",
        startMonth: 8,
        endMonth: 7,
        showMoonPhases: false,
        showWeekNumbers: true,
        weekStartsOn: 1,
        theme: "academic",
        showDayNames: true,
        showDayNumbers: true,
        showGrid: true,
        compactMode: false,
      },
    },
  ];
};

// Generate previews for all templates
const generateTemplatePreviews = async () => {
  // Queue all templates that need previews
  previewGenerationQueue.value = templates.value.filter(
    (template) =>
      template.configuration &&
      !templatePreviews.value[template.id] &&
      !template.previewSvg,
  );

  // Also set any existing previewSvg from templates
  templates.value.forEach((template) => {
    if (template.previewSvg) {
      templatePreviews.value[template.id] = scaleSvgForThumbnail(
        template.previewSvg,
      );
    }
  });

  processPreviewQueue();
};

// Process preview generation queue
const processPreviewQueue = async () => {
  if (isGeneratingPreviews.value || previewGenerationQueue.value.length === 0) {
    return;
  }

  isGeneratingPreviews.value = true;

  while (previewGenerationQueue.value.length > 0) {
    const template = previewGenerationQueue.value.shift();

    try {
      // Generate preview for this template
      const response = await fetch("/api/calendar/generate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(template.configuration),
      });

      if (response.ok) {
        const svg = await response.text();
        // Scale down the SVG for thumbnail display
        templatePreviews.value[template.id] = scaleSvgForThumbnail(svg);
      }
    } catch (error) {
      console.error(
        `Error generating preview for template ${template.id}:`,
        error,
      );
    }

    // Small delay between requests to avoid overwhelming the server
    await new Promise((resolve) => setTimeout(resolve, 100));
  }

  isGeneratingPreviews.value = false;
};

// Scale SVG for thumbnail display
const scaleSvgForThumbnail = (svg) => {
  // Parse the SVG to extract viewBox and set fixed dimensions for thumbnail
  const viewBoxMatch = svg.match(/viewBox="([^"]+)"/)?.[1];
  if (viewBoxMatch) {
    const [x, y, width, height] = viewBoxMatch.split(" ").map(Number);
    const aspectRatio = height / width;
    const thumbnailWidth = 300;
    const thumbnailHeight = thumbnailWidth * aspectRatio;

    // Replace the width and height attributes
    return svg
      .replace(/width="[^"]+"/, `width="${thumbnailWidth}"`)
      .replace(/height="[^"]+"/, `height="${thumbnailHeight}"`)
      .replace(/style="[^"]*"/, 'style="max-width: 100%; height: auto;"');
  }
  return svg;
};

// Preview template
const previewTemplate = async (template) => {
  selectedTemplate.value = template;
  showPreview.value = true;
  previewLoading.value = true;

  try {
    // If template has cached preview, use it
    if (template.previewSvg) {
      previewSvg.value = template.previewSvg;
    } else {
      // Generate preview from configuration
      const response = await fetch("/api/calendar/generate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(template.configuration),
      });

      if (response.ok) {
        previewSvg.value = await response.text();
      } else {
        throw new Error("Failed to generate preview");
      }
    }
  } catch (error) {
    console.error("Error generating preview:", error);
    toast.add({
      severity: "error",
      summary: "Error",
      detail: "Failed to generate calendar preview",
      life: 3000,
    });
    previewSvg.value = "";
  } finally {
    previewLoading.value = false;
  }
};

// Customize template
const customizeTemplate = (template) => {
  // Navigate to generator with template configuration
  router.push({
    path: "/printing/calendar-generator",
    query: {
      templateId: template.id,
    },
  });
};

// Scroll to templates section
const scrollToTemplates = () => {
  templatesSection.value?.scrollIntoView({ behavior: "smooth" });
};

// Load templates on mount
onMounted(() => {
  loadTemplates();
});
</script>

<style scoped>
.calendar-marketing {
  min-height: 100vh;
}

.hero-section {
  position: relative;
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--hero-bold-gradient);
  color: white;
  padding: 4rem 1rem;
}

.hero-content-container {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.hero-content {
  text-align: center;
  color: white;
  padding: 2rem;
  max-width: 800px;
}

.hero-title {
  font-size: 3.5rem;
  font-weight: bold;
  margin-bottom: 1rem;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
}

.hero-subtitle {
  font-size: 1.5rem;
  margin-bottom: 2rem;
  opacity: 0.95;
}

.hero-buttons {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 2.5rem;
  }
  .hero-subtitle {
    font-size: 1.2rem;
  }
}

.template-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.template-card:hover {
  transform: translateY(-4px);
}

.calendar-preview {
  max-height: 70vh;
  overflow: auto;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 1rem;
}

.calendar-preview :deep(svg) {
  max-width: 100%;
  height: auto;
}

.template-card :deep(svg) {
  max-width: 100%;
  height: auto;
  display: block;
}

.container {
  max-width: 1400px;
}

.cta-section {
  background: var(--hero-bold-gradient);
  color: white;
  padding: 4rem 1rem;
}

.cta-section h2,
.cta-section p {
  color: white;
}
</style>
