<script setup lang="ts">
type FieldOption = {
  label: string;
  value: string | number | boolean;
  description?: string;
};

type FieldItem = FieldOption | string | number | boolean;

const props = withDefaults(
  defineProps<{
    modelValue?: unknown;
    name?: string;
    label: string;
    error?: string;
    description?: string;
    hint?: string;
    placeholder?: string;
    type?:
      | "text"
      | "number"
      | "currency"
      | "percentage"
      | "color"
      | "textarea"
      | "select"
      | "searchable-select"
      | "multi-select"
      | "radio"
      | "checkbox"
      | "toggle"
      | "date"
      | "date-range"
      | "time"
      | "phone"
      | "email"
      | "password"
      | "file"
      | "drop-file"
      | "rich-text"
      | "address"
      | "autocomplete"
      | "tags";
    items?: FieldItem[];
    options?: FieldItem[];
    required?: boolean;
    disabled?: boolean;
    readonly?: boolean;
    multiple?: boolean;
    accept?: string;
    min?: number;
    max?: number;
    step?: number;
  }>(),
  {
    type: "text",
    options: () => [],
    name: undefined,
    placeholder: undefined,
    error: undefined,
    description: undefined,
    hint: undefined,
    required: false,
    disabled: false,
    readonly: false,
    multiple: false,
    accept: undefined,
    min: undefined,
    max: undefined,
    step: undefined,
  },
);

const emit = defineEmits<{
  "update:modelValue": [value: unknown];
}>();

const textModel = computed(() => props.modelValue as string | undefined);
const numberModel = computed(() => props.modelValue as number | undefined);
const booleanModel = computed(() => props.modelValue as boolean | undefined);
const singleSelectModel = computed(() => props.modelValue as string | number | boolean | undefined);
const multiSelectModel = computed(
  () => props.modelValue as Array<string | number | boolean> | undefined,
);
const dateModel = computed<any>(() => props.modelValue);
const timeModel = computed<any>(() => props.modelValue);
const fileModel = computed(() => props.modelValue as File | File[] | null | undefined);
const tagsModel = computed(() => props.modelValue as string[] | undefined);
const fieldOptions = computed(() => props.items ?? props.options);
const selectPlaceholder = computed(() => {
  const configuredPlaceholder = props.placeholder?.trim();

  return configuredPlaceholder || `Select ${props.label.toLocaleLowerCase()}`;
});

function update(value: unknown) {
  emit("update:modelValue", value);
}
</script>

<template>
  <UFormField
    class="w-full min-w-0"
    :name="name"
    :label="label"
    :description="description"
    :hint="hint"
    :error="error"
    :required="required"
  >
    <UInput
      v-if="
        ['text', 'phone', 'email', 'password', 'currency', 'percentage', 'address'].includes(type)
      "
      :model-value="textModel"
      :type="
        type === 'password'
          ? 'password'
          : type === 'email'
            ? 'email'
            : type === 'phone'
              ? 'tel'
              : 'text'
      "
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :icon="
        type === 'currency'
          ? 'i-lucide-dollar-sign'
          : type === 'percentage'
            ? 'i-lucide-percent'
            : undefined
      "
      class="w-full"
      @update:model-value="update"
    />

    <UInputNumber
      v-else-if="type === 'number'"
      :model-value="numberModel"
      :min="min"
      :max="max"
      :step="step"
      :disabled="disabled"
      :readonly="readonly"
      class="w-full"
      @update:model-value="update"
    />

    <div v-else-if="type === 'color'" class="flex items-center gap-3">
      <UInput
        :model-value="textModel"
        type="color"
        :aria-label="label"
        :disabled="disabled"
        :readonly="readonly"
        class="w-16 shrink-0"
        @update:model-value="update"
      />
      <UInput
        :model-value="textModel"
        type="text"
        :aria-label="`${label} hex value`"
        :placeholder="placeholder || '#006633'"
        :disabled="disabled"
        :readonly="readonly"
        class="min-w-0 flex-1 font-mono"
        @update:model-value="update"
      />
    </div>

    <UTextarea
      v-else-if="type === 'textarea' || type === 'rich-text'"
      :model-value="textModel"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      autoresize
      :maxrows="8"
      class="w-full"
      @update:model-value="update"
    />

    <USelect
      v-else-if="type === 'select'"
      :model-value="singleSelectModel"
      :aria-label="label"
      :items="fieldOptions"
      value-key="value"
      label-key="label"
      :placeholder="selectPlaceholder"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <USelectMenu
      v-else-if="type === 'searchable-select'"
      :model-value="singleSelectModel"
      :aria-label="label"
      :items="fieldOptions"
      value-key="value"
      label-key="label"
      :multiple="multiple"
      :placeholder="selectPlaceholder"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <USelectMenu
      v-else-if="type === 'multi-select'"
      :model-value="multiSelectModel"
      :aria-label="label"
      :items="fieldOptions"
      value-key="value"
      label-key="label"
      multiple
      :placeholder="selectPlaceholder"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <URadioGroup
      v-else-if="type === 'radio'"
      :model-value="singleSelectModel"
      :aria-label="label"
      :items="fieldOptions"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <UCheckbox
      v-else-if="type === 'checkbox'"
      :model-value="booleanModel"
      :label="placeholder || label"
      :disabled="disabled"
      @update:model-value="update"
    />

    <USwitch
      v-else-if="type === 'toggle'"
      :model-value="booleanModel"
      :disabled="disabled"
      @update:model-value="update"
    />

    <UInput
      v-else-if="type === 'date'"
      :model-value="textModel"
      type="date"
      :aria-label="label"
      :disabled="disabled"
      :readonly="readonly"
      class="w-full"
      @update:model-value="update"
    />

    <UInputDate
      v-else-if="type === 'date-range'"
      :model-value="dateModel"
      :aria-label="label"
      range
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <UInputTime
      v-else-if="type === 'time'"
      :model-value="timeModel"
      :aria-label="label"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <UFileUpload
      v-else-if="type === 'file' || type === 'drop-file'"
      :model-value="fileModel"
      :multiple="multiple"
      :accept="accept"
      :variant="type === 'drop-file' ? 'area' : 'button'"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <UInputMenu
      v-else-if="type === 'autocomplete'"
      :model-value="textModel"
      :aria-label="label"
      :items="fieldOptions"
      value-key="value"
      label-key="label"
      :placeholder="placeholder"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />

    <UInputTags
      v-else-if="type === 'tags'"
      :model-value="tagsModel"
      :aria-label="label"
      :placeholder="placeholder"
      :disabled="disabled"
      class="w-full"
      @update:model-value="update"
    />
  </UFormField>
</template>
