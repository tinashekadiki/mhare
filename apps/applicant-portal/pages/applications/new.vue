<script setup lang="ts">
// Author: Tinashe K
definePageMeta({ public: true });

const {
  auth,
  startOptions,
  loadingOptions,
  creatingApplication,
  attemptedCreate,
  pageError,
  activeStartStep,
  form,
  applicantCategoryItems,
  applicationRouteCards,
  intakeItems,
  selectedApplicationType,
  activeStartStepTitle,
  applicationJourneySteps,
  completedStartStepCount,
  applicationProgressPercentage,
  startStepValidationMessage,
  selectApplicationType,
  selectApplicationStep,
  continueStartJourney,
  loadStartOptions,
} = useApplicationStartJourney();

const applicationTypeItems = computed(() =>
  applicationRouteCards.value.map((applicationType) => ({
    label: applicationType.name,
    value: applicationType.id,
  })),
);
const navigationSteps = computed(() =>
  applicationJourneySteps.value.map((step) => ({ ...step, description: undefined })),
);
</script>

<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <EmhareTopNav :breadcrumbs="[{ label: 'Applications', to: '/' }, { label: 'New application' }]">
      <template #actions>
        <UButton
          label="Return to applications"
          icon="i-lucide-arrow-left"
          color="neutral"
          variant="ghost"
          to="/"
        />
      </template>
    </EmhareTopNav>

    <main class="mx-auto max-w-[80rem] px-4 py-8 sm:px-6 sm:py-10">
      <section v-if="!auth.authenticated.value" class="mx-auto max-w-xl py-16 text-center">
        <h1 class="text-2xl font-semibold text-uzazure-950">Sign in to start an application</h1>
        <UButton
          class="mt-6"
          label="Sign in"
          icon="i-lucide-log-in"
          color="primary"
          size="lg"
          @click="auth.login('/applications/new')"
        />
      </section>

      <div
        v-else
        class="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] lg:items-start lg:gap-8"
      >
        <aside class="min-w-0 lg:sticky lg:top-20 lg:self-start">
          <div class="mb-4 rounded-lg border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between text-sm">
              <span class="font-semibold text-slate-700">Progress</span>
              <span class="font-semibold text-uzazure-700"
                >{{ applicationProgressPercentage }}%</span
              >
            </div>
            <UProgress
              class="mt-2"
              :model-value="applicationProgressPercentage"
              color="primary"
              size="sm"
            />
            <p class="mt-2 text-xs text-slate-500">
              {{ completedStartStepCount }} of {{ applicationJourneySteps.length }} steps complete
            </p>
          </div>
          <EmhareVerticalStepper
            :steps="navigationSteps"
            :current-step="activeStartStep"
            label="Application process"
            @update:current-step="selectApplicationStep"
          />
        </aside>

        <div class="min-w-0 space-y-4">
          <UAlert
            v-if="pageError"
            color="error"
            variant="soft"
            icon="i-lucide-circle-alert"
            title="Application options unavailable"
            :description="pageError"
          />
          <section
            id="application-step-editor"
            class="min-w-0 overflow-hidden rounded-lg border border-slate-200 bg-white"
          >
            <div class="border-b border-slate-200 px-6 py-5 sm:px-8">
              <h1 class="text-xl font-semibold text-slate-900">{{ activeStartStepTitle }}</h1>
            </div>
            <form
              id="application-start-journey"
              class="space-y-5 p-6 sm:p-8"
              @submit.prevent="continueStartJourney"
            >
              <UAlert
                v-if="attemptedCreate && startStepValidationMessage"
                color="warning"
                variant="soft"
                icon="i-lucide-circle-alert"
                title="Complete required fields"
                :description="startStepValidationMessage"
              />
              <EmhareFormField
                v-model="form.applicantCategoryCode"
                name="applicantCategoryCode"
                type="select"
                label="Applicant category"
                :items="applicantCategoryItems"
                required
                :disabled="loadingOptions"
              />
              <EmhareFormField
                :model-value="form.applicationTypeId"
                name="applicationTypeId"
                type="select"
                label="Application type"
                :items="applicationTypeItems"
                required
                :disabled="loadingOptions || !applicationTypeItems.length"
                @update:model-value="selectApplicationType($event as string)"
              />
              <EmhareFormField
                v-model="form.intakeId"
                name="intakeId"
                type="select"
                label="Intake"
                :items="intakeItems"
                required
                :disabled="loadingOptions || !form.applicationTypeId"
              />
              <UAlert
                v-if="startOptions && !startOptions.routes.length"
                color="warning"
                variant="soft"
                icon="i-lucide-calendar-x-2"
                title="No applications are currently open"
              >
                <template #actions>
                  <UButton
                    label="Check again"
                    icon="i-lucide-refresh-cw"
                    color="warning"
                    variant="outline"
                    size="sm"
                    :loading="loadingOptions"
                    @click="loadStartOptions"
                  />
                </template>
              </UAlert>
              <p v-if="selectedApplicationType" class="text-sm text-slate-600">
                {{
                  selectedApplicationType.fee.required
                    ? "Application fee required"
                    : "No application fee"
                }}
              </p>
            </form>
            <footer class="flex justify-end border-t border-slate-200 px-6 py-4 sm:px-8">
              <UButton
                label="Create draft"
                icon="i-lucide-file-plus-2"
                color="primary"
                variant="solid"
                size="lg"
                :loading="creatingApplication"
                @click="continueStartJourney"
              />
            </footer>
          </section>
        </div>
      </div>
    </main>
  </div>
</template>
