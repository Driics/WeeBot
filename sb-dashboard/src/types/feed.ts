// Feed types
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

export interface FeedListResponse {
  feeds: FeedResponse[];
  total: number;
  page: number;
  size: number;
}

// Feed embed configuration
export interface FeedEmbedConfig {
  title?: string;
  description?: string;
  color?: string;
  thumbnailEnabled?: boolean;
  includeAuthor?: boolean;
  includeTimestamp?: boolean;
}
