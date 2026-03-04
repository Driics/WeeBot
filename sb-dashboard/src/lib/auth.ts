const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function loginWithDiscord() {
  window.location.href = `${BASE_URL}/api/auth/discord`;
}

export function getAuthToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)sb_token=([^;]*)/);
  return match?.[1] || undefined;
}
