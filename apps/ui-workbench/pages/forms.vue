<script setup lang="ts">
definePageMeta({ public: true, layout: 'workbench' })

const formState = reactive({
  fullName: 'Tinashe Kadiki',
  email: 'tinashe@example.com',
  phone: '+263 77 000 0000',
  applicationFee: 25,
  scholarship: 15,
  programme: 'bsc-cs',
  modules: ['cs101'],
  role: undefined as string | undefined,
  country: undefined as string | undefined,
  admissionType: 'undergraduate',
  consent: true,
  notifications: true,
  notes: 'Applicant prefers weekend communication.',
  tags: ['returning', 'priority']
})

const wizardStep = ref('profile')
const wizardSteps = [
  { id: 'profile', title: 'Profile', description: 'Identity and contact' },
  { id: 'qualifications', title: 'Qualifications', description: 'Education history' },
  { id: 'documents', title: 'Documents', description: 'Upload checklist' }
]
</script>

<template>
  <UDashboardPanel>
    <template #body>
      <EmharePageHeader
        title="Forms"
        description="Standard field wrappers, form sections, validation summary, wizard, draft status, and sticky actions."
        icon="i-lucide-pencil"
      />

      <div class="space-y-6 p-4">
        <EmhareErrorSummary :errors="['National ID is required before final submission.', 'At least one Module choice must be captured.']" />

        <UForm :state="formState" class="space-y-6">
          <EmhareFormSection title="Applicant identity" description="Common controls used by admissions and student records.">
            <EmhareFormField v-model="formState.fullName" name="fullName" label="Full name" required />
            <EmhareFormField v-model="formState.email" name="email" label="Email" type="email" required />
            <EmhareFormField v-model="formState.phone" name="phone" label="Phone" type="phone" />
            <EmhareFormField v-model="formState.notes" name="notes" label="Notes" type="textarea" />
          </EmhareFormSection>

          <EmhareFormSection title="Admissions choices" description="Select, searchable select, multi-select, number and percentage controls.">
            <EmhareFormField
              v-model="formState.programme"
              name="programme"
              label="Programme"
              type="searchable-select"
              :options="[
                { label: 'BSc Computer Science', value: 'bsc-cs' },
                { label: 'Bachelor of Commerce', value: 'bcom' }
              ]"
            />
            <EmhareFormField
              v-model="formState.modules"
              name="modules"
              label="Module choices"
              type="multi-select"
              :options="[
                { label: 'CS101 · Programming', value: 'cs101' },
                { label: 'MTH101 · Calculus', value: 'mth101' }
              ]"
            />
            <EmhareFormField
              v-model="formState.role"
              name="role"
              label="Role"
              type="select"
              :options="[
                { label: 'Admissions officer', value: 'admissions-officer' },
                { label: 'Finance officer', value: 'finance-officer' }
              ]"
            />
            <EmhareFormField
              v-model="formState.country"
              name="country"
              label="Country"
              type="searchable-select"
              :options="[
                { label: 'Zimbabwe', value: 'ZW' },
                { label: 'Zambia', value: 'ZM' }
              ]"
            />
            <EmhareFormField v-model="formState.applicationFee" name="applicationFee" label="Application fee" type="currency" />
            <EmhareFormField v-model="formState.scholarship" name="scholarship" label="Scholarship percentage" type="percentage" />
          </EmhareFormSection>

          <EmhareFieldGroup :columns="3">
            <EmhareFormField v-model="formState.admissionType" name="admissionType" label="Admission type" type="radio" :options="[{ label: 'Undergraduate', value: 'undergraduate' }, { label: 'Postgraduate', value: 'postgraduate' }]" />
            <EmhareFormField v-model="formState.consent" name="consent" label="Consent" type="checkbox" placeholder="Applicant consent captured" />
            <EmhareFormField v-model="formState.notifications" name="notifications" label="Notifications" type="toggle" />
          </EmhareFieldGroup>
        </UForm>

        <EmhareWizard v-model:current-step="wizardStep" :steps="wizardSteps">
          <template #default="{ step }">
            <UAlert color="primary" variant="soft" icon="i-lucide-info" :title="step?.title ?? 'Step'" :description="step?.description" />
          </template>
        </EmhareWizard>

        <EmhareStickyActionFooter save-label="Save draft" @save="() => {}" @cancel="() => {}">
          <template #left>
            <EmhareDraftSaveIndicator state="dirty" />
          </template>
        </EmhareStickyActionFooter>
      </div>
    </template>
  </UDashboardPanel>
</template>
