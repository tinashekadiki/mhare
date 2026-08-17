<script setup lang="ts">
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
  selectedApplicationRouteCard,
  activeStartStepTitle,
  activeStartStepDescription,
  activeStartStepIndex,
  applicationJourneySteps,
  completedStartStepCount,
  applicationProgressPercentage,
  startStepValidationMessage,
  selectApplicationType,
  selectApplicationStep,
  continueStartJourney,
  loadStartOptions,
} = useApplicationStartJourney();
</script>

<template>
  <div class="application-route-page min-h-screen bg-slate-50 text-slate-900">
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
        <p class="text-xs font-bold uppercase tracking-[0.2em] text-uzgold-700">
          Application portal
        </p>
        <h1 class="mt-4 text-3xl font-semibold tracking-tight text-uzgreen-950">
          Sign in to start an application.
        </h1>
        <p class="mx-auto mt-4 max-w-md text-base leading-7 text-slate-600">
          An account is required to save and submit an application.
        </p>
        <UButton
          class="mt-8"
          label="Sign in to continue"
          icon="i-lucide-log-in"
          color="primary"
          size="xl"
          @click="auth.login('/applications/new')"
        />
      </section>

      <div
        v-else
        class="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] lg:items-start lg:gap-8"
      >
        <aside class="min-w-0 lg:sticky lg:top-20 lg:self-start">
          <div class="mb-4 rounded-xl border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between text-sm">
              <span class="font-semibold text-slate-700">Progress</span>
              <span class="font-semibold text-uzgreen-700"
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
            :steps="applicationJourneySteps"
            :current-step="activeStartStep"
            label="Application process"
            @update:current-step="selectApplicationStep"
          />
        </aside>

        <div class="min-w-0 space-y-6">
          <UAlert
            v-if="pageError"
            color="error"
            variant="soft"
            icon="i-lucide-circle-alert"
            title="Application routes are unavailable"
            :description="pageError"
          />

          <section
            class="route-prospectus relative overflow-hidden rounded-3xl bg-uzgreen-950 px-6 py-8 text-white shadow-xl shadow-uzgreen-950/10 sm:px-9 sm:py-10"
          >
            <div class="route-prospectus__seal" aria-hidden="true">UZ</div>
            <div class="relative max-w-2xl">
              <p class="text-xs font-bold tracking-[0.24em] text-uzgold-300 uppercase">
                University admissions · {{ new Date().getFullYear() }}
              </p>
              <h1
                class="mt-4 max-w-xl font-serif text-3xl leading-tight font-semibold text-white sm:text-4xl"
              >
                Build your application on the right route.
              </h1>
              <p class="mt-4 max-w-xl text-sm leading-6 text-uzgreen-100 sm:text-base">
                Compare the evidence, open intakes and Programme catalogue before creating your
                draft. Your selection determines the rest of the application.
              </p>
            </div>
          </section>

          <section
            id="application-step-editor"
            class="min-w-0 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
          >
            <div class="border-b border-slate-100 px-6 py-5 sm:px-8">
              <p class="text-xs font-bold tracking-[0.2em] text-uzgreen-700 uppercase">
                Step {{ activeStartStepIndex + 1 }} of
                {{ applicationJourneySteps.length }}
              </p>
              <h2 class="mt-1 text-lg font-semibold text-slate-900">
                {{ activeStartStepTitle }}
              </h2>
              <p class="mt-1 text-sm text-slate-500">
                {{ activeStartStepDescription }}
              </p>
            </div>

            <form
              id="application-start-journey"
              class="p-6 sm:p-8"
              @submit.prevent="continueStartJourney"
            >
              <UAlert
                v-if="attemptedCreate && startStepValidationMessage"
                class="mb-6"
                color="warning"
                variant="soft"
                icon="i-lucide-circle-alert"
                title="Complete required fields"
                :description="startStepValidationMessage"
              />

              <div class="max-w-sm">
                <EmhareFormField
                  v-model="form.applicantCategoryCode"
                  type="select"
                  label="Applicant category"
                  description="This determines the applicable fee policy."
                  :items="applicantCategoryItems"
                  required
                  :disabled="loadingOptions"
                />
              </div>

              <div class="mt-8">
                <p class="text-xs font-bold tracking-[0.2em] text-uzgold-700 uppercase">
                  Route directory
                </p>
                <h2 class="mt-2 font-serif text-2xl font-semibold text-uzgreen-950">
                  Choose your application route
                </h2>
                <p class="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
                  Choose by the qualification you intend to pursue. Programme availability is
                  governed by the selected intake.
                </p>

                <div
                  v-if="loadingOptions"
                  class="mt-6 grid gap-4 md:grid-cols-2"
                  aria-label="Loading application routes"
                >
                  <USkeleton v-for="index in 4" :key="index" class="h-56 rounded-2xl" />
                </div>
                <div v-else class="mt-6 grid gap-4 md:grid-cols-2">
                  <button
                    v-for="applicationType in applicationRouteCards"
                    :key="applicationType.id"
                    type="button"
                    :data-testid="`application-route-${applicationType.normalizedCode}`"
                    :aria-pressed="form.applicationTypeId === applicationType.id"
                    class="route-card group relative min-h-56 overflow-hidden rounded-2xl border p-5 text-left transition duration-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-uzgreen-700"
                    :class="
                      form.applicationTypeId === applicationType.id
                        ? 'border-uzgreen-700 bg-uzgreen-950 text-white shadow-lg shadow-uzgreen-950/15'
                        : 'border-slate-200 bg-white text-slate-900 hover:-translate-y-0.5 hover:border-uzgreen-300 hover:shadow-lg hover:shadow-uzgreen-950/8'
                    "
                    @click="selectApplicationType(applicationType.id)"
                  >
                    <span class="flex items-start justify-between gap-4">
                      <span
                        class="grid size-11 shrink-0 place-items-center rounded-xl border"
                        :class="
                          form.applicationTypeId === applicationType.id
                            ? 'border-uzgold-400/50 bg-uzgold-400/10 text-uzgold-300'
                            : 'border-uzgreen-100 bg-uzgreen-50 text-uzgreen-800'
                        "
                      >
                        <UIcon :name="applicationType.icon" class="size-5" />
                      </span>
                      <span
                        class="font-serif text-3xl leading-none"
                        :class="
                          form.applicationTypeId === applicationType.id
                            ? 'text-uzgold-300'
                            : 'text-slate-200'
                        "
                        >{{ applicationType.routeNumber }}</span
                      >
                    </span>
                    <span
                      class="mt-5 block text-[0.68rem] font-bold tracking-[0.18em] uppercase"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'text-uzgold-300'
                          : 'text-uzgreen-700'
                      "
                      >{{ applicationType.code }}</span
                    >
                    <span
                      class="mt-1 block text-lg font-semibold"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'text-white'
                          : 'text-uzgreen-950'
                      "
                      >{{ applicationType.name }}</span
                    >
                    <span
                      class="mt-2 block text-sm leading-5"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'text-uzgreen-100'
                          : 'text-slate-600'
                      "
                      >{{ applicationType.description }}</span
                    >
                    <span
                      class="mt-5 flex flex-wrap gap-x-4 gap-y-2 border-t pt-4 text-xs font-medium"
                      :class="
                        form.applicationTypeId === applicationType.id
                          ? 'border-white/15 text-uzgreen-100'
                          : 'border-slate-100 text-slate-500'
                      "
                    >
                      <span
                        >{{ applicationType.intakeCount }} open intake{{
                          applicationType.intakeCount === 1 ? "" : "s"
                        }}</span
                      >
                      <span
                        >{{ applicationType.programmeCount }} Programme{{
                          applicationType.programmeCount === 1 ? "" : "s"
                        }}</span
                      >
                    </span>
                    <UIcon
                      v-if="form.applicationTypeId === applicationType.id"
                      name="i-lucide-circle-check-big"
                      class="absolute right-5 bottom-5 size-5 text-uzgold-300"
                    />
                  </button>
                </div>
              </div>

              <UAlert
                v-if="startOptions && !startOptions.routes.length"
                class="mt-6"
                color="warning"
                variant="soft"
                icon="i-lucide-calendar-x-2"
                title="No application route is currently open"
                description="Admissions must activate a fully configured route whose Programme mappings intersect an open intake."
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

              <div
                v-if="selectedApplicationRouteCard"
                class="mt-8 grid gap-6 rounded-2xl border border-uzgreen-200 bg-uzgreen-50/70 p-5 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,0.8fr)] lg:p-6"
              >
                <div data-testid="selected-route-evidence">
                  <p class="text-xs font-bold tracking-[0.18em] text-uzgreen-700 uppercase">
                    Prepare before you begin
                  </p>
                  <h3 class="mt-2 text-base font-semibold text-uzgreen-950">
                    Evidence for {{ selectedApplicationRouteCard.name }}
                  </h3>
                  <ul class="mt-4 grid gap-2 sm:grid-cols-2">
                    <li
                      v-for="evidence in selectedApplicationRouteCard.evidence"
                      :key="evidence"
                      class="flex items-center gap-2 text-sm text-slate-700"
                    >
                      <UIcon name="i-lucide-check" class="size-4 shrink-0 text-uzgreen-700" />
                      {{ evidence }}
                    </li>
                  </ul>
                  <p class="mt-4 text-xs leading-5 text-slate-500">
                    Qualification and supporting-document requirements are confirmed inside the
                    draft from the route configuration.
                  </p>
                </div>
                <div class="rounded-xl border border-white bg-white p-4 shadow-sm">
                  <EmhareFormField
                    v-model="form.intakeId"
                    type="select"
                    label="Intake"
                    description="Only intakes offering this route are listed."
                    :items="intakeItems"
                    placeholder="Select an open intake"
                    required
                    :disabled="loadingOptions"
                  />
                  <div class="mt-4 border-t border-slate-100 pt-4">
                    <p
                      class="text-sm font-semibold"
                      :class="
                        selectedApplicationType?.fee.required
                          ? 'text-uzgold-800'
                          : 'text-uzgreen-800'
                      "
                    >
                      {{
                        selectedApplicationType?.fee.required
                          ? "Application fee required"
                          : "No application fee"
                      }}
                    </p>
                    <p class="mt-1 text-xs leading-5 text-slate-500">
                      {{
                        selectedApplicationType?.fee.policyStatus === "FEE_STRUCTURE"
                          ? "Finance calculates the governed fee after your programme choices are known. It must be confirmed or waived before Admissions review."
                          : selectedApplicationType?.fee.policyStatus === "FEE_FREE"
                            ? "This route has an audited fee-free decision."
                            : "Admissions must configure this route's fee policy before an application can be started."
                      }}
                    </p>
                  </div>
                </div>
              </div>
            </form>

            <footer
              class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 bg-slate-50/60 px-6 py-4 sm:px-8"
            >
              <span class="text-sm text-slate-500">Fields marked * are required.</span>
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

<style scoped>
.application-route-page {
  background-image:
    radial-gradient(circle at 8% 14%, rgb(246 189 37 / 8%), transparent 26rem),
    linear-gradient(180deg, rgb(237 248 240 / 55%), transparent 26rem);
}

.route-prospectus::after {
  position: absolute;
  inset: 0;
  background-image: repeating-linear-gradient(
    115deg,
    transparent 0 18px,
    rgb(255 255 255 / 2%) 18px 19px
  );
  content: "";
  pointer-events: none;
}

.route-prospectus__seal {
  position: absolute;
  top: 50%;
  right: clamp(1rem, 6vw, 5rem);
  display: grid;
  width: 8rem;
  height: 8rem;
  translate: 0 -50%;
  place-items: center;
  border: 1px solid rgb(246 189 37 / 25%);
  border-radius: 9999px;
  color: rgb(246 189 37 / 12%);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 3rem;
  font-weight: 700;
  letter-spacing: -0.08em;
}

.route-card[aria-pressed="true"] {
  background: var(--color-uzgreen-950);
}

@media (max-width: 639px) {
  .route-prospectus__seal {
    right: -2.5rem;
    width: 7rem;
    height: 7rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .route-card {
    transition: none;
  }
}
</style>
