// Author: Tinashe K

import { expect, test, type Page } from "@playwright/test";

const studentPortalUrl = process.env.STUDENT_PORTAL_URL ?? "http://localhost:3002";
const applicantPortalUrl = process.env.APPLICANT_PORTAL_URL ?? "http://localhost:3001";
const adminPortalUrl = process.env.ADMIN_PORTAL_URL ?? "http://localhost:3000";

async function openPublicGateway(page: Page) {
  await page.goto(studentPortalUrl, { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("heading", { name: "Choose your portal" })).toBeVisible({
    timeout: 15_000,
  });
}

const event = {
  publicationId: "publication-event",
  itemId: "item-event",
  versionId: "version-event",
  kind: "EVENT",
  slug: "open-day-2026",
  title: "University Open Day",
  summary: "Meet academic teams and explore student services.",
  schemaVersion: 1,
  structuredContent: [
    { type: "PARAGRAPH", text: "Prospective students and families are welcome." },
  ],
  publishFrom: "2026-08-17T08:00:00Z",
  pinned: false,
  featured: false,
  event: {
    startsAt: "2026-09-12T07:00:00Z",
    endsAt: "2026-09-12T13:00:00Z",
    timezone: "Africa/Harare",
    attendanceMode: "IN_PERSON",
    venueName: "Great Hall",
  },
};

const news = {
  publicationId: "publication-news",
  itemId: "item-news",
  versionId: "version-news",
  kind: "NEWS",
  slug: "research-week",
  title: "Research week opens",
  summary: "A week of public scholarship and discovery.",
  schemaVersion: 1,
  structuredContent: [{ type: "PARAGRAPH", text: "Join the university research community." }],
  publishFrom: "2026-08-17T08:00:00Z",
  pinned: false,
  featured: false,
};

const secondNews = {
  ...news,
  publicationId: "publication-news-2",
  itemId: "item-news-2",
  versionId: "version-news-2",
  slug: "innovation-hub",
  title: "Innovation hub welcomes student founders",
  summary: "New mentoring and prototyping support opens to university teams.",
};

const thirdNews = {
  ...news,
  publicationId: "publication-news-3",
  itemId: "item-news-3",
  versionId: "version-news-3",
  slug: "library-digital-collections",
  title: "Library expands digital collections",
  summary: "Students and researchers can now access additional academic resources.",
};

const notice = {
  ...news,
  publicationId: "publication-notice",
  itemId: "item-notice",
  versionId: "version-notice",
  kind: "NOTICE",
  slug: "registration-deadline",
  title: "Registration deadline reminder",
  summary: "Complete outstanding registration actions before the published deadline.",
  pinned: true,
};

const alert = {
  ...notice,
  publicationId: "publication-alert",
  itemId: "item-alert",
  versionId: "version-alert",
  kind: "ALERT",
  slug: "scheduled-maintenance",
  title: "Scheduled service maintenance",
  summary: "Selected online services will be briefly unavailable on Sunday morning.",
};

const importantLink = {
  ...news,
  publicationId: "publication-link",
  itemId: "item-link",
  versionId: "version-link",
  kind: "LINK",
  slug: "university-library",
  title: "University library",
  summary: "Search catalogues and digital resources.",
  externalUrl: "https://library.uz.ac.zw",
};

const campaign = {
  ...news,
  publicationId: "publication-campaign",
  itemId: "item-campaign",
  versionId: "version-campaign",
  kind: "CAMPAIGN",
  slug: "new-student-orientation",
  title: "New student orientation 2026",
  summary: "Find the programme, venues, and services that will help you start well.",
  featured: true,
};

test.describe("single public eMhare gateway", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/api/communications/public/home", (route) =>
      route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          urgentNotices: [notice, alert],
          importantLinks: [
            importantLink,
            {
              ...importantLink,
              publicationId: "publication-link-fees",
              slug: "fees-information",
              title: "Fees information",
            },
            {
              ...importantLink,
              publicationId: "publication-link-calendar",
              slug: "academic-calendar",
              title: "Academic calendar",
            },
          ],
          featuredCampaign: campaign,
          upcomingEvents: [event],
          latestNews: [news, secondNews, thirdNews],
        }),
      }),
    );
    await page.route("**/api/communications/public/items/*", (route) => {
      const item = route.request().url().includes("open-day-2026") ? event : news;
      return route.fulfill({ contentType: "application/json", body: JSON.stringify(item) });
    });
  });

  test("loads anonymously and routes every code-owned portal card", async ({ page }, testInfo) => {
    const consoleErrors: string[] = [];
    const failedRequests: string[] = [];
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("requestfailed", (request) =>
      failedRequests.push(`${request.method()} ${request.url()}`),
    );

    await openPublicGateway(page);
    await expect(page.getByRole("img", { name: "University of Zimbabwe" })).toHaveAttribute(
      "src",
      "/images/brand/university-of-zimbabwe-logo.png",
    );
    await expect
      .poll(() =>
        page
          .getByRole("link", { name: /Apply or track an application/i })
          .evaluate((link: HTMLAnchorElement) => link.href),
      )
      .toBe(new URL(`${applicantPortalUrl}/`).href);
    await expect
      .poll(() =>
        page
          .getByRole("link", { name: /Open student portal/i })
          .evaluate((link: HTMLAnchorElement) => link.href),
      )
      .toBe(new URL(`${studentPortalUrl}/student`).href);
    await expect
      .poll(() =>
        page
          .getByRole("link", { name: /Open staff portal/i })
          .evaluate((link: HTMLAnchorElement) => link.href),
      )
      .toBe(new URL(`${adminPortalUrl}/`).href);
    const latestNewsRegion = page.getByRole("region", { name: "Latest news" });
    await expect(latestNewsRegion.getByRole("heading", { name: "Latest news" })).toBeVisible();
    await expect(
      latestNewsRegion.getByRole("heading", { name: "Research week opens" }),
    ).toBeVisible();
    await expect(page.getByText("Registration deadline reminder")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Important links" })).toBeVisible();
    await expect(page.locator('img[src="/images/gateway/student-community.webp"]')).toBeVisible();
    const featuredUpdatesRegion = page.getByRole("region", {
      name: "Featured university updates",
    });
    const researchImageSelector = 'img[src="/images/gateway/research-innovation.webp"]';
    await expect(featuredUpdatesRegion.locator(researchImageSelector)).toHaveCount(1);
    await expect(latestNewsRegion.locator(researchImageSelector)).toHaveCount(2);
    await expect(
      featuredUpdatesRegion.locator('img[src="/images/gateway/library-learning.webp"]'),
    ).toBeVisible();
    await expect
      .poll(() =>
        page
          .locator('img[src="/images/gateway/student-community.webp"]')
          .evaluate((image: HTMLImageElement) => image.naturalWidth),
      )
      .toBeGreaterThan(0);
    const libraryImage = featuredUpdatesRegion.locator(
      'img[src="/images/gateway/library-learning.webp"]',
    );
    await libraryImage.scrollIntoViewIfNeeded();
    await expect
      .poll(() => libraryImage.evaluate((image: HTMLImageElement) => image.naturalWidth))
      .toBeGreaterThan(0);

    await page.screenshot({ path: testInfo.outputPath("public-gateway.png"), fullPage: true });

    const sliderTrack = page.locator('[aria-roledescription="carousel"] > div').first();
    await page.getByRole("button", { name: "Show next image" }).click();
    await expect(sliderTrack).toHaveAttribute("style", /translateX\(-100%\)/);

    expect(consoleErrors).toEqual([]);
    expect(failedRequests).toEqual([]);
  });

  test("shows an event detail and exposes its calendar download without horizontal overflow", async ({
    page,
  }) => {
    await openPublicGateway(page);
    await page.getByRole("link", { name: "University Open Day" }).click();
    await expect(page).toHaveURL(/\/events\/open-day-2026$/);
    await expect(page.getByRole("heading", { name: "University Open Day" })).toBeVisible();
    await expect(page.getByText("Prospective students and families are welcome.")).toBeVisible();
    await expect(page.getByRole("link", { name: /Download calendar event/i })).toHaveAttribute(
      "href",
      /calendar\.ics$/,
    );

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });
});
