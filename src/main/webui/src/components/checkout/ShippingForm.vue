<script setup lang="ts">
import { ref, computed, watch } from "vue";
import InputText from "primevue/inputtext";
import InputMask from "primevue/inputmask";
import Dropdown from "primevue/dropdown";

interface ShippingAddress {
  firstName: string;
  lastName: string;
  company?: string;
  street: string;
  street2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  phone: string;
  email: string;
}

interface ValidationErrors {
  [key: string]: string;
}

interface Props {
  modelValue: ShippingAddress;
  errors?: ValidationErrors;
  disabled?: boolean;
}

interface Emits {
  (e: "update:modelValue", value: ShippingAddress): void;
  (e: "validate"): boolean;
}

const props = withDefaults(defineProps<Props>(), {
  errors: () => ({}),
  disabled: false,
});

const emit = defineEmits<Emits>();

// Local state for v-model
const localAddress = ref<ShippingAddress>({ ...props.modelValue });

// Watch for external changes
watch(
  () => props.modelValue,
  (newVal) => {
    localAddress.value = { ...newVal };
  },
  { deep: true },
);

// Emit changes
const updateField = (field: keyof ShippingAddress, value: any) => {
  localAddress.value[field] = value;
  emit("update:modelValue", { ...localAddress.value });
};

// Countries
const countries = [
  { code: "US", name: "United States" },
  { code: "CA", name: "Canada" },
  { code: "MX", name: "Mexico" },
];

// US States
const usStates = [
  { code: "AL", name: "Alabama" },
  { code: "AK", name: "Alaska" },
  { code: "AZ", name: "Arizona" },
  { code: "AR", name: "Arkansas" },
  { code: "CA", name: "California" },
  { code: "CO", name: "Colorado" },
  { code: "CT", name: "Connecticut" },
  { code: "DE", name: "Delaware" },
  { code: "FL", name: "Florida" },
  { code: "GA", name: "Georgia" },
  { code: "HI", name: "Hawaii" },
  { code: "ID", name: "Idaho" },
  { code: "IL", name: "Illinois" },
  { code: "IN", name: "Indiana" },
  { code: "IA", name: "Iowa" },
  { code: "KS", name: "Kansas" },
  { code: "KY", name: "Kentucky" },
  { code: "LA", name: "Louisiana" },
  { code: "ME", name: "Maine" },
  { code: "MD", name: "Maryland" },
  { code: "MA", name: "Massachusetts" },
  { code: "MI", name: "Michigan" },
  { code: "MN", name: "Minnesota" },
  { code: "MS", name: "Mississippi" },
  { code: "MO", name: "Missouri" },
  { code: "MT", name: "Montana" },
  { code: "NE", name: "Nebraska" },
  { code: "NV", name: "Nevada" },
  { code: "NH", name: "New Hampshire" },
  { code: "NJ", name: "New Jersey" },
  { code: "NM", name: "New Mexico" },
  { code: "NY", name: "New York" },
  { code: "NC", name: "North Carolina" },
  { code: "ND", name: "North Dakota" },
  { code: "OH", name: "Ohio" },
  { code: "OK", name: "Oklahoma" },
  { code: "OR", name: "Oregon" },
  { code: "PA", name: "Pennsylvania" },
  { code: "RI", name: "Rhode Island" },
  { code: "SC", name: "South Carolina" },
  { code: "SD", name: "South Dakota" },
  { code: "TN", name: "Tennessee" },
  { code: "TX", name: "Texas" },
  { code: "UT", name: "Utah" },
  { code: "VT", name: "Vermont" },
  { code: "VA", name: "Virginia" },
  { code: "WA", name: "Washington" },
  { code: "WV", name: "West Virginia" },
  { code: "WI", name: "Wisconsin" },
  { code: "WY", name: "Wyoming" },
];

// Canadian Provinces
const canadianProvinces = [
  { code: "AB", name: "Alberta" },
  { code: "BC", name: "British Columbia" },
  { code: "MB", name: "Manitoba" },
  { code: "NB", name: "New Brunswick" },
  { code: "NL", name: "Newfoundland and Labrador" },
  { code: "NT", name: "Northwest Territories" },
  { code: "NS", name: "Nova Scotia" },
  { code: "NU", name: "Nunavut" },
  { code: "ON", name: "Ontario" },
  { code: "PE", name: "Prince Edward Island" },
  { code: "QC", name: "Quebec" },
  { code: "SK", name: "Saskatchewan" },
  { code: "YT", name: "Yukon" },
];

// Mexican States
const mexicanStates = [
  { code: "AGU", name: "Aguascalientes" },
  { code: "BCN", name: "Baja California" },
  { code: "BCS", name: "Baja California Sur" },
  { code: "CAM", name: "Campeche" },
  { code: "CHP", name: "Chiapas" },
  { code: "CHH", name: "Chihuahua" },
  { code: "CMX", name: "Ciudad de México" },
  { code: "COA", name: "Coahuila" },
  { code: "COL", name: "Colima" },
  { code: "DUR", name: "Durango" },
  { code: "GUA", name: "Guanajuato" },
  { code: "GRO", name: "Guerrero" },
  { code: "HID", name: "Hidalgo" },
  { code: "JAL", name: "Jalisco" },
  { code: "MEX", name: "Estado de México" },
  { code: "MIC", name: "Michoacán" },
  { code: "MOR", name: "Morelos" },
  { code: "NAY", name: "Nayarit" },
  { code: "NLE", name: "Nuevo León" },
  { code: "OAX", name: "Oaxaca" },
  { code: "PUE", name: "Puebla" },
  { code: "QUE", name: "Querétaro" },
  { code: "ROO", name: "Quintana Roo" },
  { code: "SLP", name: "San Luis Potosí" },
  { code: "SIN", name: "Sinaloa" },
  { code: "SON", name: "Sonora" },
  { code: "TAB", name: "Tabasco" },
  { code: "TAM", name: "Tamaulipas" },
  { code: "TLA", name: "Tlaxcala" },
  { code: "VER", name: "Veracruz" },
  { code: "YUC", name: "Yucatán" },
  { code: "ZAC", name: "Zacatecas" },
];

// States based on country
const states = computed(() => {
  switch (localAddress.value.country) {
    case "CA":
      return canadianProvinces;
    case "MX":
      return mexicanStates;
    default:
      return usStates;
  }
});

// State/Province label
const stateLabel = computed(() => {
  switch (localAddress.value.country) {
    case "CA":
      return "Province";
    case "MX":
      return "State";
    default:
      return "State";
  }
});

// Clear state when country changes
watch(
  () => localAddress.value.country,
  () => {
    updateField("state", "");
  },
);

// Validate form
const validate = (): boolean => {
  const errors: ValidationErrors = {};

  if (!localAddress.value.email) errors.email = "Email is required";
  if (!localAddress.value.firstName)
    errors.firstName = "First name is required";
  if (!localAddress.value.lastName) errors.lastName = "Last name is required";
  if (!localAddress.value.street) errors.street = "Street address is required";
  if (!localAddress.value.city) errors.city = "City is required";
  if (!localAddress.value.state)
    errors.state = `${stateLabel.value} is required`;
  if (!localAddress.value.postalCode)
    errors.postalCode = "Postal code is required";
  if (!localAddress.value.phone) errors.phone = "Phone number is required";

  return Object.keys(errors).length === 0;
};

defineExpose({ validate });
</script>

<template>
  <div class="shipping-form">
    <h3 class="form-section-title">Contact Information</h3>

    <div class="form-group">
      <label for="email">Email</label>
      <InputText
        id="email"
        :model-value="localAddress.email"
        type="email"
        placeholder="your.email@example.com"
        class="w-full"
        :class="{ 'p-invalid': errors.email }"
        :disabled="disabled"
        @update:model-value="updateField('email', $event)"
      />
      <small v-if="errors.email" class="p-error">{{ errors.email }}</small>
    </div>

    <h3 class="form-section-title">Shipping Address</h3>

    <div class="form-row">
      <div class="form-group">
        <label for="firstName">First Name</label>
        <InputText
          id="firstName"
          :model-value="localAddress.firstName"
          placeholder="John"
          class="w-full"
          :class="{ 'p-invalid': errors.firstName }"
          :disabled="disabled"
          @update:model-value="updateField('firstName', $event)"
        />
        <small v-if="errors.firstName" class="p-error">{{
          errors.firstName
        }}</small>
      </div>

      <div class="form-group">
        <label for="lastName">Last Name</label>
        <InputText
          id="lastName"
          :model-value="localAddress.lastName"
          placeholder="Doe"
          class="w-full"
          :class="{ 'p-invalid': errors.lastName }"
          :disabled="disabled"
          @update:model-value="updateField('lastName', $event)"
        />
        <small v-if="errors.lastName" class="p-error">{{
          errors.lastName
        }}</small>
      </div>
    </div>

    <div class="form-group">
      <label for="company">Company (optional)</label>
      <InputText
        id="company"
        :model-value="localAddress.company"
        placeholder="Company name"
        class="w-full"
        :disabled="disabled"
        @update:model-value="updateField('company', $event)"
      />
    </div>

    <div class="form-group">
      <label for="street">Street Address</label>
      <InputText
        id="street"
        :model-value="localAddress.street"
        placeholder="123 Main St"
        class="w-full"
        :class="{ 'p-invalid': errors.street }"
        :disabled="disabled"
        @update:model-value="updateField('street', $event)"
      />
      <small v-if="errors.street" class="p-error">{{ errors.street }}</small>
    </div>

    <div class="form-group">
      <label for="street2">Apartment, Suite, etc. (optional)</label>
      <InputText
        id="street2"
        :model-value="localAddress.street2"
        placeholder="Apt 4B"
        class="w-full"
        :disabled="disabled"
        @update:model-value="updateField('street2', $event)"
      />
    </div>

    <div class="form-group">
      <label for="country">Country</label>
      <Dropdown
        id="country"
        :model-value="localAddress.country"
        :options="countries"
        option-label="name"
        option-value="code"
        placeholder="Select a country"
        class="w-full"
        :disabled="disabled"
        @update:model-value="updateField('country', $event)"
      />
    </div>

    <div class="form-row three-col">
      <div class="form-group">
        <label for="city">City</label>
        <InputText
          id="city"
          :model-value="localAddress.city"
          placeholder="Nashville"
          class="w-full"
          :class="{ 'p-invalid': errors.city }"
          :disabled="disabled"
          @update:model-value="updateField('city', $event)"
        />
        <small v-if="errors.city" class="p-error">{{ errors.city }}</small>
      </div>

      <div class="form-group">
        <label for="state">{{ stateLabel }}</label>
        <Dropdown
          id="state"
          :model-value="localAddress.state"
          :options="states"
          option-label="name"
          option-value="code"
          :placeholder="`Select ${stateLabel.toLowerCase()}`"
          class="w-full"
          :class="{ 'p-invalid': errors.state }"
          :disabled="disabled"
          @update:model-value="updateField('state', $event)"
        />
        <small v-if="errors.state" class="p-error">{{ errors.state }}</small>
      </div>

      <div class="form-group">
        <label for="postalCode">
          {{ localAddress.country === "US" ? "ZIP Code" : "Postal Code" }}
        </label>
        <InputMask
          v-if="localAddress.country === 'US'"
          id="postalCode"
          :model-value="localAddress.postalCode"
          mask="99999"
          placeholder="12345"
          class="w-full"
          :class="{ 'p-invalid': errors.postalCode }"
          :disabled="disabled"
          @update:model-value="updateField('postalCode', $event)"
        />
        <InputMask
          v-else-if="localAddress.country === 'CA'"
          id="postalCode"
          :model-value="localAddress.postalCode"
          mask="a9a 9a9"
          placeholder="A1A 1A1"
          class="w-full"
          :class="{ 'p-invalid': errors.postalCode }"
          :disabled="disabled"
          @update:model-value="updateField('postalCode', $event)"
        />
        <InputText
          v-else
          id="postalCode"
          :model-value="localAddress.postalCode"
          placeholder="Postal code"
          class="w-full"
          :class="{ 'p-invalid': errors.postalCode }"
          :disabled="disabled"
          @update:model-value="updateField('postalCode', $event)"
        />
        <small v-if="errors.postalCode" class="p-error">{{
          errors.postalCode
        }}</small>
      </div>
    </div>

    <div class="form-group">
      <label for="phone">Phone Number</label>
      <InputMask
        id="phone"
        :model-value="localAddress.phone"
        mask="(999) 999-9999"
        placeholder="(555) 123-4567"
        class="w-full"
        :class="{ 'p-invalid': errors.phone }"
        :disabled="disabled"
        @update:model-value="updateField('phone', $event)"
      />
      <small v-if="errors.phone" class="p-error">{{ errors.phone }}</small>
    </div>
  </div>
</template>

<style scoped>
.shipping-form {
  max-width: 600px;
}

.form-section-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 1rem;
  margin-top: 1.5rem;
}

.form-section-title:first-child {
  margin-top: 0;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  font-size: 0.875rem;
  font-weight: 500;
  color: #374151;
  margin-bottom: 0.375rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.form-row.three-col {
  grid-template-columns: 2fr 1fr 1fr;
}

@media (max-width: 768px) {
  .form-row,
  .form-row.three-col {
    grid-template-columns: 1fr;
  }
}
</style>
