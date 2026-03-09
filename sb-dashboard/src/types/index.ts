// Auth
export interface UserInfo {
  id: string;
  username: string;
  avatar: string | null;
  guilds: GuildInfo[];
}

export interface GuildInfo {
  id: string;
  name: string;
  icon: string | null;
  permissions: number;
  botPresent: boolean;
}

// Config
export interface GuildConfig {
  general: GeneralConfig;
  moderation: ModerationConfig;
  music: MusicConfig;
  audit: AuditConfig;
}

export interface GeneralConfig {
  language: string;
  botNickname: string;
}

export interface ModerationConfig {
  autoModEnabled: boolean;
  antiSpamEnabled: boolean;
  antiSpamThreshold: number;
  antiLinkEnabled: boolean;
  antiProfanityEnabled: boolean;
  antiRaidEnabled: boolean;
  antiRaidThreshold: number;
  wordBlacklist: string[];
  warnEscalationRules: WarnEscalationRule[];
}

export interface WarnEscalationRule {
  warnCount: number;
  action: "mute" | "kick" | "ban";
  duration?: string;
}

export interface MusicConfig {
  defaultVolume: number;
  djRoleId: string | null;
  maxQueueSize: number;
  twentyFourSevenEnabled: boolean;
}

export interface AuditConfig {
  logChannelId: string | null;
  loggedEvents: string[];
}

// Moderation
export interface CaseResponse {
  id: number;
  caseNumber: number;
  guildId: string;
  type: "warn" | "mute" | "kick" | "ban" | "unban" | "unmute";
  targetId: string;
  targetUsername: string;
  moderatorId: string;
  moderatorUsername: string;
  reason: string | null;
  duration: string | null;
  createdAt: string;
}

export interface CaseListResponse {
  cases: CaseResponse[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface CaseFilterParams {
  type?: string;
  moderatorId?: string;
  targetId?: string;
  search?: string;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortDirection?: "asc" | "desc";
}

// Stats
export interface StatsOverview {
  memberCount: number;
  activeToday: number;
  commandsLast24h: number;
  modActionsLast7d: number;
}

export interface CommandUsage {
  period: string;
  data: { label: string; count: number }[];
}

export interface MemberGrowth {
  period: string;
  data: { date: string; joins: number; leaves: number; total: number }[];
}

export interface AudioStats {
  period: string;
  data: { date: string; tracksPlayed: number; listenTimeMinutes: number }[];
}

// API Keys
export interface ApiKeyResponse {
  id: string;
  maskedKey: string;
  scopes: string[];
  createdBy: string;
  createdAt: string;
  lastUsedAt: string | null;
}

export interface CreateApiKeyRequest {
  scopes: string[];
}

export interface CreateApiKeyResponse {
  id: string;
  rawKey: string;
  scopes: string[];
}

// Guild resources
export interface GuildRole {
  id: string;
  name: string;
  color: number;
  position: number;
}

export interface GuildChannel {
  id: string;
  name: string;
  type: "text" | "voice" | "category";
}

// WebSocket events
export interface WsStatsEvent {
  type: "stats_update";
  guildId: string;
  overview: Partial<StatsOverview>;
}

export interface WsModerationEvent {
  type: "case_created" | "case_updated";
  guildId: string;
  case: CaseResponse;
}

export interface WsAudioEvent {
  type: "track_started" | "player_paused" | "queue_updated";
  guildId: string;
  data: Record<string, unknown>;
}

// Feeds
export type FeedType = "REDDIT" | "TWITCH" | "YOUTUBE";

export interface FeedResponse {
  id: number;
  guildId: string;
  feedType: FeedType;
  targetIdentifier: string;
  targetChannelId: string;
  checkIntervalMinutes: number;
  embedConfig: Record<string, unknown> | null;
  enabled: boolean;
  lastCheckTime: string | null;
  lastItemId: string | null;
  createdAt: string;
}

export interface FeedListResponse {
  feeds: FeedResponse[];
  total: number;
  page: number;
  size: number;
}

export interface CreateFeedRequest {
  feedType: FeedType;
  targetIdentifier: string;
  targetChannelId: string;
  checkIntervalMinutes?: number;
  embedConfig?: Record<string, unknown> | null;
  enabled?: boolean;
}

export interface UpdateFeedRequest {
  targetIdentifier?: string;
  targetChannelId?: string;
  checkIntervalMinutes?: number;
  embedConfig?: Record<string, unknown> | null;
  enabled?: boolean;
}

// API error
export interface ApiError {
  error: string;
  message: string;
  status: number;
}

// Reaction Roles
export * from "./reaction-roles";
