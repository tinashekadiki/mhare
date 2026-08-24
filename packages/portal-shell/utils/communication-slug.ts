// Author: Tinashe K

export function communicationSlugPreview(title: string) {
  const normalizedTitle = title
    .normalize("NFKD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase();
  const slug = normalizedTitle.replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
  return (slug || "public-item").slice(0, 180).replace(/-+$/g, "");
}
