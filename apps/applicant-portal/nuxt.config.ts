export default defineNuxtConfig({
  extends: ["../../packages/portal-shell"],
  compatibilityDate: "2026-08-06",
  devtools: { enabled: false },
  app: {
    baseURL: process.env.NODE_ENV === "production" ? "/applicant/" : "/",
    head: {
      title: "eMhare Applicant",
      meta: [{ name: "viewport", content: "width=device-width, initial-scale=1" }],
    },
  },
});
