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
  CreateReactionRoleGroupRequest,
  CreateReactionRoleMenuItemRequest,
  CreateReactionRoleMenuRequest,
  CreateFeedRequest,
  FeedListResponse,
  FeedResponse,
  GuildChannel,
  GuildConfig,
  GuildRole,
  MemberGrowth,
  PostMenuResponse,
  ReactionRoleActionResponse,
  ReactionRoleGroupListResponse,
  ReactionRoleGroupResponse,
  ReactionRoleMenuListResponse,
  ReactionRoleMenuResponse,
  ReactionRoleMenuType,
  StatsOverview,
  TicketFilterParams,
  TicketListResponse,
  TicketMetrics,
  TicketResponse,
  UpdateReactionRoleGroupRequest,
  UpdateReactionRoleMenuItemRequest,
  UpdateReactionRoleMenuRequest,
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

  // Reaction Roles - Menus
  getReactionRoleMenus(
    guildId: string,
    params?: { page?: number; size?: number; menuType?: ReactionRoleMenuType }
  ): Promise<ReactionRoleMenuListResponse> {
    const query = new URLSearchParams();
    if (params) {
      if (params.page !== undefined) query.set("page", String(params.page));
      if (params.size !== undefined) query.set("size", String(params.size));
      if (params.menuType) query.set("menuType", params.menuType);
    }
    const qs = query.toString();
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus${qs ? `?${qs}` : ""}`);
  }

  getReactionRoleMenu(guildId: string, menuId: number): Promise<ReactionRoleMenuResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus/${menuId}`);
  }

  createReactionRoleMenu(guildId: string, data: CreateReactionRoleMenuRequest): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  updateReactionRoleMenu(
    guildId: string,
    menuId: number,
    data: UpdateReactionRoleMenuRequest
  ): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus/${menuId}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  deleteReactionRoleMenu(guildId: string, menuId: number): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus/${menuId}`, {
      method: "DELETE",
    });
  }

  postReactionRoleMenu(guildId: string, menuId: number): Promise<PostMenuResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus/${menuId}/post`, {
      method: "POST",
    });
  }

  updatePostedReactionRoleMenu(guildId: string, menuId: number): Promise<PostMenuResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus/${menuId}/update`, {
      method: "POST",
    });
  }

  // Reaction Roles - Menu Items
  createReactionRoleMenuItem(
    guildId: string,
    menuId: number,
    data: CreateReactionRoleMenuItemRequest
  ): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/menus/${menuId}/items`, {
      method: "POST",
      body: JSON.stringify(data),
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

  updateReactionRoleMenuItem(
    guildId: string,
    itemId: number,
    data: UpdateReactionRoleMenuItemRequest
  ): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/items/${itemId}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  updateFeed(guildId: string, feedId: number, data: UpdateFeedRequest): Promise<FeedResponse> {
    return this.request(`/api/guilds/${guildId}/feeds/${feedId}`, {
      method: "PATCH",
      body: JSON.stringify(data),
    });
  }

  deleteReactionRoleMenuItem(guildId: string, itemId: number): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/items/${itemId}`, {
      method: "DELETE",
    });
  }

  deleteFeed(guildId: string, feedId: number): Promise<void> {
    return this.request(`/api/guilds/${guildId}/feeds/${feedId}`, {
      method: "DELETE",
    });
  }

  // Reaction Roles - Groups
  getReactionRoleGroups(
    guildId: string,
    params?: { page?: number; size?: number }
  ): Promise<ReactionRoleGroupListResponse> {
    const query = new URLSearchParams();
    if (params) {
      if (params.page !== undefined) query.set("page", String(params.page));
      if (params.size !== undefined) query.set("size", String(params.size));
    }
    const qs = query.toString();
    return this.request(`/api/guilds/${guildId}/reaction-roles/groups${qs ? `?${qs}` : ""}`);
  }

  getReactionRoleGroup(guildId: string, groupId: number): Promise<ReactionRoleGroupResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/groups/${groupId}`);
  }

  createReactionRoleGroup(guildId: string, data: CreateReactionRoleGroupRequest): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/groups`, {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  updateReactionRoleGroup(
    guildId: string,
    groupId: number,
    data: UpdateReactionRoleGroupRequest
  ): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/groups/${groupId}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  }

  deleteReactionRoleGroup(guildId: string, groupId: number): Promise<ReactionRoleActionResponse> {
    return this.request(`/api/guilds/${guildId}/reaction-roles/groups/${groupId}`, {
      method: "DELETE",
    });
  }

  testFeed(guildId: string, feedId: number): Promise<Record<string, unknown>> {
    return this.request(`/api/guilds/${guildId}/feeds/${feedId}/test`, {
      method: "POST",
    });
  }

  // Tickets
  getTickets(guildId: string, params?: TicketFilterParams): Promise<TicketListResponse> {
    const query = new URLSearchParams();
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
          query.set(key, String(value));
        }
      });
    }
    const qs = query.toString();
    return this.request(`/api/guilds/${guildId}/tickets${qs ? `?${qs}` : ""}`);
  }

  getTicket(guildId: string, ticketId: number): Promise<TicketResponse> {
    return this.request(`/api/guilds/${guildId}/tickets/${ticketId}`);
  }

  getTicketMetrics(guildId: string): Promise<TicketMetrics> {
    return this.request(`/api/guilds/${guildId}/tickets/metrics`);
  }
}

export const api = new ApiClient();
