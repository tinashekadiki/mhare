import type {
  PublicCommunicationHome,
  PublicCommunicationItem,
} from "@emhare/portal-shell/types/communications";

function communicationsApiBase() {
  const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env;
  return env?.NUXT_PUBLIC_API_BASE ?? "http://localhost:8080";
}

export function usePublicCommunications() {
  const auth = useEmhareAuth();
  const apiBase = communicationsApiBase();

  function home() {
    return $fetch<PublicCommunicationHome>(`${apiBase}/api/communications/public/home`);
  }

  function item(slug: string) {
    return $fetch<PublicCommunicationItem>(
      `${apiBase}/api/communications/public/items/${encodeURIComponent(slug)}`,
    );
  }

  function calendarUrl(slug: string) {
    return `${apiBase}/api/communications/public/events/${encodeURIComponent(slug)}/calendar.ics`;
  }

  function mediaUrl(assetId: string) {
    return `${apiBase}/api/communications/public/media/${encodeURIComponent(assetId)}`;
  }

  async function recordAuthenticatedRead(publicationId: string) {
    await auth.loadUser();
    if (!auth.accessToken.value) {
      return;
    }
    await $fetch(`${apiBase}/api/communications/publications/${publicationId}/read`, {
      method: "PUT",
      headers: { Authorization: `Bearer ${auth.accessToken.value}` },
    });
  }

  return { home, item, calendarUrl, mediaUrl, recordAuthenticatedRead };
}
