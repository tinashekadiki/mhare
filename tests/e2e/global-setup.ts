// Author: Tinashe K

const keycloakBaseUrl = process.env.KEYCLOAK_URL ?? "http://localhost:8099";
const keycloakRealm = process.env.KEYCLOAK_REALM ?? "emhare";
const keycloakAdminUsername = process.env.KEYCLOAK_ADMIN_USERNAME ?? "admin";
const keycloakAdminPassword = process.env.KEYCLOAK_ADMIN_PASSWORD ?? "admin";
const portalClientId = "emhare-web";

type KeycloakClientRepresentation = {
  id: string;
  clientId: string;
  redirectUris?: string[];
  webOrigins?: string[];
  [key: string]: unknown;
};

async function requireSuccessfulResponse(response: Response, action: string) {
  if (response.ok) return;
  const responseBody = await response.text();
  throw new Error(`${action} failed with HTTP ${response.status}: ${responseBody}`);
}

function configuredLocalPortalOrigins() {
  return [
    process.env.ADMIN_PORTAL_URL,
    process.env.APPLICANT_PORTAL_URL,
    process.env.STUDENT_PORTAL_URL,
  ]
    .filter((url): url is string => Boolean(url))
    .map((url) => new URL(url))
    .filter((url) => ["localhost", "127.0.0.1"].includes(url.hostname))
    .map((url) => url.origin);
}

export default async function configurePlaywrightKeycloakRedirects() {
  const portalOrigins = configuredLocalPortalOrigins();
  if (!portalOrigins.length) return;

  const tokenResponse = await fetch(
    `${keycloakBaseUrl}/realms/master/protocol/openid-connect/token`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: "admin-cli",
        username: keycloakAdminUsername,
        password: keycloakAdminPassword,
        grant_type: "password",
      }),
    },
  );
  await requireSuccessfulResponse(tokenResponse, "Keycloak administrator authentication");
  const { access_token: accessToken } = (await tokenResponse.json()) as { access_token: string };
  const authorizationHeaders = { Authorization: `Bearer ${accessToken}` };

  const clientsResponse = await fetch(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/clients?clientId=${portalClientId}`,
    { headers: authorizationHeaders },
  );
  await requireSuccessfulResponse(clientsResponse, "Keycloak portal client lookup");
  const clients = (await clientsResponse.json()) as KeycloakClientRepresentation[];
  const portalClient = clients.find((client) => client.clientId === portalClientId);
  if (!portalClient) throw new Error(`Keycloak client ${portalClientId} was not found.`);

  const clientResponse = await fetch(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/clients/${portalClient.id}`,
    { headers: authorizationHeaders },
  );
  await requireSuccessfulResponse(clientResponse, "Keycloak portal client read");
  const clientRepresentation = (await clientResponse.json()) as KeycloakClientRepresentation;
  clientRepresentation.redirectUris = [
    ...new Set([
      ...(clientRepresentation.redirectUris ?? []),
      ...portalOrigins.map((origin) => `${origin}/*`),
    ]),
  ];
  clientRepresentation.webOrigins = [
    ...new Set([...(clientRepresentation.webOrigins ?? []), ...portalOrigins]),
  ];

  const updateResponse = await fetch(
    `${keycloakBaseUrl}/admin/realms/${keycloakRealm}/clients/${portalClient.id}`,
    {
      method: "PUT",
      headers: { ...authorizationHeaders, "Content-Type": "application/json" },
      body: JSON.stringify(clientRepresentation),
    },
  );
  await requireSuccessfulResponse(updateResponse, "Keycloak Playwright redirect registration");
}
