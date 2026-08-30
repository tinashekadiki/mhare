<script setup lang="ts">
// Author: Tinashe K
defineEmits<{
  "create-account": [];
  "sign-in": [];
}>();
</script>

<template>
  <main class="bg-white text-slate-900">
    <section
      id="admissions"
      class="border-b border-slate-200 bg-uzorange-50"
      aria-labelledby="admissions-heading"
    >
      <div
        class="mx-auto grid max-w-7xl gap-10 px-5 py-10 sm:px-8 sm:py-14 lg:grid-cols-[minmax(0,1fr)_23rem] lg:items-center lg:gap-16"
      >
        <div>
          <h1
            id="admissions-heading"
            class="max-w-2xl font-serif text-4xl leading-[1.12] tracking-tight text-uzazure-800 sm:text-5xl lg:text-6xl"
          >
            University of Zimbabwe <span class="block italic">admissions</span>
          </h1>
          <p class="mt-5 max-w-lg text-base leading-7 text-slate-600">
            Apply for a programme, upload your documents and check your application status.
          </p>
          <button
            type="button"
            data-testid="create-applicant-account"
            class="mt-7 inline-flex min-h-12 items-center justify-center gap-5 rounded-sm bg-uzazure-700 px-6 py-3 text-sm font-bold text-white hover:bg-uzazure-900 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-uzazure-700"
            @click="$emit('create-account')"
          >
            Create account <UIcon name="i-lucide-arrow-right" class="size-4" />
          </button>
        </div>

        <aside
          class="border-t-4 border-uzorange-500 bg-uzazure-800 p-6 text-white sm:p-8"
          aria-labelledby="returning-applicant-heading"
        >
          <h2 id="returning-applicant-heading" class="font-serif text-3xl leading-tight">
            Continue your application
          </h2>
          <p class="mt-4 text-sm leading-6 text-uzazure-100">
            Sign in with the account you used to apply.
          </p>
          <ul class="mt-5 space-y-3 border-t border-white/20 pt-5 text-sm text-white">
            <li class="flex gap-3">
              <UIcon name="i-lucide-file-text" class="size-5 shrink-0 text-uzorange-300" /> Continue
              a saved application
            </li>
            <li class="flex gap-3">
              <UIcon name="i-lucide-list-checks" class="size-5 shrink-0 text-uzorange-300" /> Check
              documents and payment status
            </li>
            <li class="flex gap-3">
              <UIcon name="i-lucide-file-check-2" class="size-5 shrink-0 text-uzorange-300" /> View
              and respond to an offer
            </li>
          </ul>
          <button
            type="button"
            data-testid="applicant-sign-in"
            class="mt-7 flex min-h-12 w-full items-center justify-between gap-3 rounded-sm bg-white px-4 py-3 text-sm font-bold text-uzazure-800 hover:bg-uzorange-100 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white"
            @click="$emit('sign-in')"
          >
            Sign in <UIcon name="i-lucide-arrow-right" class="size-4" />
          </button>
        </aside>
      </div>
    </section>

    <div
      class="mx-auto grid max-w-7xl gap-10 px-5 py-10 sm:px-8 sm:py-12 lg:grid-cols-[minmax(0,1fr)_23rem] lg:gap-16"
    >
      <section id="how-to-apply" aria-labelledby="application-process-heading">
        <h2 id="application-process-heading" class="font-serif text-3xl text-uzazure-800">
          How to apply
        </h2>
        <ol class="mt-6 divide-y divide-slate-200 border-t border-slate-200">
          <li class="grid grid-cols-[2rem_1fr] gap-4 py-5">
            <span class="font-serif text-xl text-uzorange-700" aria-hidden="true">01</span>
            <div>
              <h3 class="font-bold">Create your account</h3>
              <p class="mt-1 text-sm leading-6 text-slate-600">
                Register, then sign in to start your application.
              </p>
            </div>
          </li>
          <li class="grid grid-cols-[2rem_1fr] gap-4 py-5">
            <span class="font-serif text-xl text-uzorange-700" aria-hidden="true">02</span>
            <div>
              <h3 class="font-bold">Choose your programmes</h3>
              <p class="mt-1 text-sm leading-6 text-slate-600">
                Choose an available application type and list your programmes in order of
                preference.
              </p>
            </div>
          </li>
          <li class="grid grid-cols-[2rem_1fr] gap-4 py-5">
            <span class="font-serif text-xl text-uzorange-700" aria-hidden="true">03</span>
            <div>
              <h3 class="font-bold">Upload your documents</h3>
              <p class="mt-1 text-sm leading-6 text-slate-600">
                Add the required documents. If your application has a fee, you need confirmed
                payment or an authorised waiver before you submit.
              </p>
            </div>
          </li>
          <li class="grid grid-cols-[2rem_1fr] gap-4 py-5">
            <span class="font-serif text-xl text-uzorange-700" aria-hidden="true">04</span>
            <div>
              <h3 class="font-bold">Submit your application</h3>
              <p class="mt-1 text-sm leading-6 text-slate-600">
                Check that all required sections are complete, then submit for review. Sign in to
                check its status.
              </p>
            </div>
          </li>
        </ol>
      </section>

      <section
        id="before-you-apply"
        class="self-start border border-slate-200 bg-slate-50 p-6 sm:p-7"
        aria-labelledby="preparation-heading"
      >
        <h2 id="preparation-heading" class="font-serif text-3xl text-uzazure-800">
          Before you apply
        </h2>
        <dl class="mt-6 space-y-5 text-sm">
          <div>
            <dt class="font-bold">Email address</dt>
            <dd class="mt-1 leading-6 text-slate-600">Use an email address you can access.</dd>
          </div>
          <div>
            <dt class="font-bold">Identity and academic documents</dt>
            <dd class="mt-1 leading-6 text-slate-600">
              Prepare clear copies of your identity document and academic results. Your application
              lists the documents you need.
            </dd>
          </div>
          <div>
            <dt class="font-bold">File formats and size</dt>
            <dd class="mt-1 leading-6 text-slate-600">
              Use PDF, JPEG or PNG files, up to 10 MB each. Check that names, grades and document
              numbers are legible.
            </dd>
          </div>
        </dl>
      </section>
    </div>

    <section id="questions" class="border-t border-slate-200" aria-labelledby="questions-heading">
      <div
        class="mx-auto grid max-w-7xl gap-6 px-5 py-10 sm:px-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] lg:gap-16"
      >
        <div>
          <h2 id="questions-heading" class="font-serif text-3xl text-uzazure-800">
            Questions about applying
          </h2>
        </div>
        <div class="divide-y divide-slate-200 border-y border-slate-200 text-sm">
          <details class="group py-4">
            <summary
              class="cursor-pointer py-1 font-bold text-uzazure-800 focus-visible:outline-2 focus-visible:outline-offset-4"
            >
              Can I return to an unfinished application?
            </summary>
            <p class="mt-3 leading-6 text-slate-600">
              Yes. Sign in with the same account to open your draft and finish the remaining
              sections. Admissions can review your application only after you submit it.
            </p>
          </details>
          <details class="group py-4">
            <summary
              class="cursor-pointer py-1 font-bold text-uzazure-800 focus-visible:outline-2 focus-visible:outline-offset-4"
            >
              Do I need to pay an application fee?
            </summary>
            <p class="mt-3 leading-6 text-slate-600">
              Some application types have a fee. If yours does, you can submit once payment is
              confirmed or an authorised waiver is recorded.
            </p>
          </details>
          <details class="group py-4">
            <summary
              class="cursor-pointer py-1 font-bold text-uzazure-800 focus-visible:outline-2 focus-visible:outline-offset-4"
            >
              Where will I find my admission decision?
            </summary>
            <p class="mt-3 leading-6 text-slate-600">
              Sign in to check your application status. When Admissions publishes an offer, you can
              read the official letter and accept or decline it in your account.
            </p>
          </details>
        </div>
      </div>
    </section>
  </main>
</template>
