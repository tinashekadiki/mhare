export type EmharePortalKind = "student" | "applicant" | "staff";

type PortalLocation = Pick<Location, "port"> &
  Partial<Pick<Location, "hostname" | "origin" | "pathname">>;
type PortalEnvironment = Record<string, string | undefined>;

function configuredPortalOrigin(
  environment: PortalEnvironment | undefined,
  kind: EmharePortalKind,
) {
  const environmentKind = kind.toUpperCase();
  return (
    environment?.[`VITE_EMHARE_${environmentKind}_PORTAL_URL`] ??
    environment?.[`NUXT_PUBLIC_${environmentKind}_PORTAL_URL`]
  );
}

function configuredPortalKind(
  location: PortalLocation,
  environment = (import.meta as unknown as { env?: PortalEnvironment }).env,
): EmharePortalKind | undefined {
  const explicitKind = environment?.VITE_EMHARE_PORTAL_KIND;
  if (explicitKind === "staff" || explicitKind === "applicant" || explicitKind === "student") {
    return explicitKind;
  }
  if (!location.origin) return undefined;
  const currentOrigin = location.origin.replace(/\/$/, "");
  return (["staff", "applicant", "student"] as const).find((kind) => {
    const configuredOrigin = configuredPortalOrigin(environment, kind);
    return configuredOrigin?.replace(/\/$/, "") === currentOrigin;
  });
}

export function inferPortalKind(
  location: PortalLocation,
  environment?: PortalEnvironment,
): EmharePortalKind {
  if (location.pathname === "/applicant" || location.pathname?.startsWith("/applicant/")) {
    return "applicant";
  }
  if (location.pathname === "/staff" || location.pathname?.startsWith("/staff/")) {
    return "staff";
  }
  if (location.port === "3001") {
    return "applicant";
  }
  if (location.port === "3000") {
    return "staff";
  }
  const configuredKind = configuredPortalKind(location, environment);
  if (configuredKind) {
    return configuredKind;
  }
  return "student";
}

export function portalPrefix(kind: EmharePortalKind) {
  return kind === "student" ? "/student" : `/${kind}`;
}

function localPortDevelopment(
  location: Pick<Location, "hostname" | "port"> & Partial<Pick<Location, "origin" | "pathname">>,
) {
  return (
    (location.hostname === "localhost" || location.hostname === "127.0.0.1") &&
    (["3000", "3001", "3002"].includes(location.port) || Boolean(configuredPortalKind(location)))
  );
}

export function portalCallbackPath(
  kind: EmharePortalKind,
  location?: Pick<Location, "hostname" | "port">,
) {
  if (location && localPortDevelopment(location)) {
    return "/auth/callback";
  }
  return kind === "student" ? "/auth/callback" : `/${kind}/auth/callback`;
}

export function sanitizePortalReturnPath(
  candidate: string | undefined | null,
  origin: string,
  kind: EmharePortalKind,
  location?: Pick<Location, "hostname" | "port">,
) {
  const localDevelopment = Boolean(location && localPortDevelopment(location));
  const fallback =
    localDevelopment && kind !== "student"
      ? kind === "staff"
        ? "/operations"
        : "/"
      : portalPrefix(kind);
  if (!candidate) {
    return fallback;
  }
  try {
    const target = new URL(candidate, origin);
    if (target.origin !== origin) {
      return fallback;
    }
    if (localDevelopment && kind !== "student") {
      if (target.pathname.startsWith("/auth/")) return fallback;
    } else {
      const prefix = portalPrefix(kind);
      if (target.pathname !== prefix && !target.pathname.startsWith(`${prefix}/`)) {
        return fallback;
      }
    }
    return `${target.pathname}${target.search}${target.hash}`;
  } catch {
    return fallback;
  }
}

export function portalDestinationUrl(
  kind: EmharePortalKind,
  environment = (import.meta as unknown as { env?: PortalEnvironment }).env,
) {
  if (import.meta.server) {
    return portalPrefix(kind);
  }
  const override = configuredPortalOrigin(environment, kind);
  if (override) {
    return override;
  }
  const { hostname, protocol } = window.location;
  if (hostname === "localhost" || hostname === "127.0.0.1") {
    const port = kind === "staff" ? "3000" : kind === "applicant" ? "3001" : "3002";
    const localPath = kind === "student" ? "/student" : "/";
    return `${protocol}//${hostname}:${port}${localPath}`;
  }
  return portalPrefix(kind);
}
