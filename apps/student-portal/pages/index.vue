<script setup lang="ts">
import type { PublicCommunicationItem } from "@emhare/portal-shell/types/communications";

definePageMeta({ public: true });

const communications = usePublicCommunications();
const {
  data: home,
  pending,
  error,
  refresh,
} = await useAsyncData("public-communications-home", communications.home);

const portalCards = computed(() => [
  {
    title: "Applicants",
    icon: "i-lucide-file-check-2",
    actionLabel: "Apply or track an application",
    href: portalDestinationUrl("applicant"),
  },
  {
    title: "Students",
    icon: "i-lucide-graduation-cap",
    actionLabel: "Open student portal",
    href: portalDestinationUrl("student"),
  },
  {
    title: "Staff & Faculty",
    icon: "i-lucide-shield-check",
    actionLabel: "Open staff portal",
    href: portalDestinationUrl("staff"),
  },
]);

const publishedNews = computed(() => home.value?.latestNews ?? []);
const gatewayImages = {
  studentCommunity: "/images/gateway/student-community.webp",
  researchInnovation: "/images/gateway/research-innovation.webp",
  libraryLearning: "/images/gateway/library-learning.webp",
} as const;

function editorialFallbackImage(item: PublicCommunicationItem, index: number) {
  const searchableText = `${item.title} ${item.summary}`.toLowerCase();
  if (/library|book|study|digital collection|academic resource/.test(searchableText)) {
    return gatewayImages.libraryLearning;
  }
  if (/research|innovation|laboratory|science|prototype/.test(searchableText)) {
    return gatewayImages.researchInnovation;
  }
  return index % 2 === 0 ? gatewayImages.researchInnovation : gatewayImages.libraryLearning;
}

const gatewaySlides = computed(() => {
  const slides: Array<{
    image: string;
    alternativeText: string;
    title?: string;
    href?: string;
    actionLabel?: string;
  }> = [];
  const campaign = home.value?.featuredCampaign;
  if (campaign) {
    slides.push({
      image: campaign.mediaUrl ?? gatewayImages.studentCommunity,
      alternativeText: campaign.mediaUrl
        ? campaign.title
        : "University students walking together on campus",
      title: campaign.title,
      href: `/campaigns/${campaign.slug}`,
      actionLabel: "View campaign",
    });
  }

  const institutionalSlides = [
    {
      image: gatewayImages.studentCommunity,
      alternativeText: "University students walking together on campus",
    },
    {
      image: gatewayImages.researchInnovation,
      alternativeText: "Students collaborating in a university research laboratory",
    },
    {
      image: gatewayImages.libraryLearning,
      alternativeText: "Students studying together in a university library",
    },
  ];
  for (const slide of institutionalSlides) {
    if (slides.length === 3) break;
    if (!slides.some((existingSlide) => existingSlide.image === slide.image)) slides.push(slide);
  }
  return slides;
});

function communicationHref(item: PublicCommunicationItem) {
  if (item.kind === "LINK" && item.externalUrl) return item.externalUrl;
  if (item.kind === "NEWS") return `/news/${item.slug}`;
  if (item.kind === "EVENT") return `/events/${item.slug}`;
  if (item.kind === "CAMPAIGN") return `/campaigns/${item.slug}`;
  return `/notices/${item.slug}`;
}
</script>

<template>
  <div class="min-h-screen overflow-x-hidden bg-slate-50 text-slate-950">
    <EmharePublicGatewayHeader />

    <main>
      <section
        id="portal-access"
        class="border-b border-slate-200 bg-white"
        aria-labelledby="portal-access-heading"
      >
        <div class="mx-auto max-w-7xl px-5 py-7 sm:px-8 sm:py-8">
          <h1 id="portal-access-heading" class="text-2xl font-black tracking-tight">
            Choose your portal
          </h1>
          <div class="mt-5 grid gap-4 md:grid-cols-3">
            <EmharePortalDestinationCard
              v-for="card in portalCards"
              :key="card.title"
              v-bind="card"
            />
          </div>
        </div>
      </section>

      <div v-if="error" class="mx-auto max-w-7xl px-5 pt-5 sm:px-8">
        <div
          class="flex flex-col gap-3 border-l-4 border-uzorange-500 bg-uzorange-50 px-4 py-3 text-sm text-slate-700 sm:flex-row sm:items-center sm:justify-between"
          role="status"
        >
          <p>
            <span class="font-bold text-slate-900"
              >Public updates are temporarily unavailable.</span
            >
            Portal access remains available.
          </p>
          <button
            type="button"
            class="w-fit font-bold text-uzazure-700 underline decoration-uzazure-300 underline-offset-4"
            @click="refresh()"
          >
            Try again
          </button>
        </div>
      </div>

      <section class="mx-auto max-w-7xl px-5 py-6 sm:px-8">
        <EmharePublicGatewaySlider :slides="gatewaySlides" />
      </section>

      <section
        v-if="pending || home?.urgentNotices.length || home?.importantLinks.length"
        class="mx-auto grid max-w-7xl gap-6 px-5 pb-8 sm:px-8 lg:grid-cols-[minmax(0,2fr)_minmax(18rem,1fr)]"
      >
        <div
          v-if="pending || home?.urgentNotices.length"
          id="notices"
          class="border border-slate-200 bg-white"
          aria-labelledby="notices-heading"
        >
          <div class="flex items-center gap-3 bg-uzorange-500 px-5 py-3 text-slate-950">
            <UIcon name="i-lucide-megaphone" class="size-5" />
            <h2 id="notices-heading" class="text-lg font-black">Notices and alerts</h2>
          </div>
          <div class="p-5">
            <div v-if="pending" class="grid gap-4 md:grid-cols-2">
              <USkeleton v-for="index in 2" :key="index" class="h-40" />
            </div>
            <div
              v-else-if="home?.urgentNotices.length"
              class="grid gap-4"
              :class="{ 'md:grid-cols-2': home.urgentNotices.length > 1 }"
            >
              <EmharePublicContentCard
                v-for="item in home.urgentNotices"
                :key="item.publicationId"
                :item="item"
                compact
              />
            </div>
          </div>
        </div>

        <aside
          v-if="home?.importantLinks.length"
          id="services"
          class="border border-slate-200 bg-white"
          aria-labelledby="services-heading"
        >
          <div class="bg-uzazure-700 px-5 py-3 text-white">
            <h2 id="services-heading" class="text-lg font-black">Important links</h2>
          </div>
          <ul class="divide-y divide-slate-200">
            <li v-for="item in home.importantLinks" :key="item.publicationId">
              <a
                :href="communicationHref(item)"
                class="group flex items-center justify-between gap-4 px-5 py-4 text-sm font-bold text-slate-800 hover:bg-uzazure-50 hover:text-uzazure-700"
              >
                <span>{{ item.title }}</span>
                <UIcon name="i-lucide-external-link" class="size-4 shrink-0 text-uzazure-600" />
              </a>
            </li>
          </ul>
        </aside>
      </section>

      <section
        v-if="home?.upcomingEvents.length"
        id="events"
        class="border-y border-slate-200 bg-white"
        aria-labelledby="events-heading"
      >
        <div class="mx-auto max-w-7xl px-5 py-9 sm:px-8 sm:py-11">
          <h2
            id="events-heading"
            class="border-b border-slate-200 pb-4 text-2xl font-black tracking-tight"
          >
            Upcoming events
          </h2>
          <div class="grid gap-x-8 lg:grid-cols-2">
            <EmharePublicEventCard
              v-for="item in home.upcomingEvents"
              :key="item.publicationId"
              :item="item"
            />
          </div>
        </div>
      </section>

      <section
        v-if="publishedNews.length"
        id="news"
        class="mx-auto max-w-7xl px-5 py-9 sm:px-8 sm:py-11"
        aria-labelledby="news-heading"
      >
        <div class="flex items-center justify-between gap-6 border-b-2 border-uzazure-600 pb-3">
          <div>
            <h2 id="news-heading" class="text-2xl font-black tracking-tight">Latest news</h2>
          </div>
          <UIcon name="i-lucide-newspaper" class="size-7 text-uzazure-700" />
        </div>
        <div class="mt-5 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          <EmharePublicContentCard
            v-for="(item, index) in publishedNews"
            :key="item.publicationId"
            :item="item"
            :fallback-image="editorialFallbackImage(item, index)"
          />
        </div>
      </section>
    </main>
  </div>
</template>
