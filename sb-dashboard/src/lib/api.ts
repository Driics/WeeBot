import type {
  ApiError,
  ApiKeyResponse,
  AudioStats,
  CaseFilterParams,
  CaseListResponse,
  CaseResponse,
  CommandUsage,
  CreateApiKeyRequest,
  CreateApiKeyResponse,
  CreateFeedRequest,
  FeedListResponse,
  FeedResponse,
  GuildChannel,
  GuildConfig,
  GuildRole,
  MemberGrowth,
  StatsOverview,
  UpdateFeedRequest,
  UserInfo,
} from "@/types";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

class ApiClient {
  private async request<T>(path: string, options?: RequestInit): Promise<T> {
    const res = await fetch(`${BASE_URL}${path}`, {
      ...options,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...options?.headers,
      },
    });

    if (!res.ok) {
      const error: ApiError = await res.json().catch(() => ({
        error: "unknown",
        message: res.statusText,
        status: res.status,
      }));
      throw error;
    }

    if (res.status === 204) return undefined as unknown as T;
    return res.json();
  }

  // Auth
  getCurrentUser(): Promise<UserInfo> {
    return this.request("/api/auth/me");
  }

  async logout(): Promise<void> {
    await this.request("/api/auth/logout", { method: "POST" });
  }

  // Guild Config
  getGuildConfig(guildId: string): Promise<GuildConfig> {
    return this.request(`/api/guilds/${guildId}/config`);
  }

  updateGuildConfig(guildId: string, config: Partial<GuildConfig>): Promise<GuildConfig> {
    return this.request(`/api/guilds/${guildId}/config`, {
      method: "PATCH",
      body: JSON.stringify(config),
    });
  }

  getGuildRoles(guildId: string): Promise<GuildRole[]> {
    return this.request(`/api/guilds/${guildId}/roles`);
  }

  getGuildChannels(guildId: string): Promise<GuildChannel[]> {
    return this.request(`/api/guilds/${guildId}/channels`);
  }

  // Moderation
  getCases(guildId: string, params?: CaseFilterParams): Promise<CaseListResponse> {
    const query = new URLSearchParams();
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
          query.set(key, String(value));
        }
      });
    }
    const qs = query.toString();
    return this.request(`/api/guilds/${guildId}/cases${qs ? `?${qs}` : ""}`);
  }

  getCase(guildId: string, caseId: number): Promise<CaseResponse> {
    return this.request(`/api/guilds/${guildId}/cases/${caseId}`);
  }

  executeBan(guildId: string, data: { targetId: string; reason?: string; duration?: string }): Promise<CaseResponse> {
    return this.request(`/api/guilds/${guildId}/moderation/ban`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  executeKick(guildId: string, data: { targetId: string; reason?: string }): Promise<CaseResponse> {
    return this.request(`/api/guilds/${guildId}/moderation/kick`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  executeWarn(guildId: string, data: { targetId: string; reason?: string }): Promise<CaseResponse> {
    return this.request(`/api/guilds/${guildId}/moderation/warn`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  // Stats
  getStatsOverview(guildId: string): Promise<StatsOverview> {
    return this.request(`/api/guilds/${guildId}/stats/overview`);
  }

  getCommandUsage(guildId: string, period: string = "7d"): Promise<CommandUsage> {
    const params = new URLSearchParams({ period });
    return this.request(`/api/guilds/${guildId}/stats/commands?${params}`);
  }

  getMemberGrowth(guildId: string, period: string = "30d"): Promise<MemberGrowth> {
    const params = new URLSearchParams({ period });
    return this.request(`/api/guilds/${guildId}/stats/members?${params}`);
  }

  getAudioStats(guildId: string, period: string = "7d"): Promise<AudioStats> {
    const params = new URLSearchParams({ period });
    return this.request(`/api/guilds/${guildId}/stats/audio?${params}`);
  }

  // API Keys
  getApiKeys(guildId: string): Promise<ApiKeyResponse[]> {
    return this.request(`/api/guilds/${guildId}/api-keys`);
  }

  createApiKey(guildId: string, data: CreateApiKeyRequest): Promise<CreateApiKeyResponse> {
    return this.request(`/api/guilds/${guildId}/api-keys`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  revokeApiKey(guildId: string, keyId: string): Promise<void> {
    return this.request(`/api/guilds/${guildId}/api-keys/${keyId}`, {
      method: "DELETE",
    });
  }

  // Feeds
  getFeeds(guildId: string): Promise<FeedListResponse> {
    return this.request(`/api/guilds/${guildId}/feeds`);
  }

  createFeed(guildId: string, data: CreateFeedRequest): Promise<FeedResponse> {
    return this.request(`/api/guilds/${guildId}/feeds`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  updateFeed(guildId: string, feedId: number, data: UpdateFeedRequest): Promise<FeedResponse> {
    return this.request(`/api/guilds/${guildId}/feeds/${feedId}`, {
      method: "PATCH",
      body: JSON.stringify(data),
    });
  }

  deleteFeed(guildId: string, feedId: number): Promise<void> {
    return this.request(`/api/guilds/${guildId}/feeds/${feedId}`, {
      method: "DELETE",
    });
  }

  testFeed(guildId: string, feedId: number): Promise<Record<string, unknown>> {
    return this.request(`/api/guilds/${guildId}/feeds/${feedId}/test`, {
      method: "POST",
    });
  }
}

export const api = new ApiClient();
