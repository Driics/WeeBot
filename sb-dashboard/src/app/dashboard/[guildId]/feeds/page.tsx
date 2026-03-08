"use client";

import { use, useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { FeedListResponse, FeedResponse } from "@/types";

export default function FeedsPage({
  params,
}: {
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);
  const [data, setData] = useState<FeedListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchFeeds = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .getFeeds(guildId)
      .then(setData)
      .catch((e) => setError(e.message || "Failed to load feeds"))
      .finally(() => setLoading(false));
  }, [guildId]);

  useEffect(() => {
    fetchFeeds();
  }, [fetchFeeds]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="text-2xl font-bold">Social Media Feeds</h2>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : error ? (
        <div className="text-center py-8">
          <p className="text-destructive">{error}</p>
          <button onClick={fetchFeeds} className="mt-2 text-sm text-primary underline">Retry</button>
        </div>
      ) : data && data.feeds.length > 0 ? (
        <div className="rounded-lg border">
          <table className="w-full">
            <thead className="border-b bg-muted/50">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium">Type</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Target</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Channel</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Interval</th>
                <th className="px-4 py-3 text-left text-sm font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {data.feeds.map((feed) => (
                <tr key={feed.id} className="border-b last:border-b-0">
                  <td className="px-4 py-3 text-sm">{feed.feedType}</td>
                  <td className="px-4 py-3 text-sm font-mono">{feed.targetIdentifier}</td>
                  <td className="px-4 py-3 text-sm">
                    <span className="font-mono text-xs">#{feed.targetChannelId}</span>
                  </td>
                  <td className="px-4 py-3 text-sm">{feed.checkIntervalMinutes}m</td>
                  <td className="px-4 py-3 text-sm">
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ${
                        feed.enabled
                          ? "bg-green-100 text-green-800"
                          : "bg-gray-100 text-gray-800"
                      }`}
                    >
                      {feed.enabled ? "Enabled" : "Disabled"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="text-center py-12 border rounded-lg">
          <p className="text-muted-foreground">No feeds configured yet</p>
          <p className="text-sm text-muted-foreground mt-1">Create your first feed to get started</p>
        </div>
      )}
    </div>
  );
}
