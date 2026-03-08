"use client";

import { use, useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { StatsOverview } from "@/types";
import { OverviewCards } from "@/components/stats/OverviewCards";

export default function GuildOverviewPage({
  params,
}: {
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);
  const [stats, setStats] = useState<StatsOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .getStatsOverview(guildId)
      .then(setStats)
      .catch((e) => setError(e.message || "Failed to load data"))
      .finally(() => setLoading(false));
  }, [guildId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-8">
        <p className="text-destructive">{error}</p>
        <button onClick={fetchData} className="mt-2 text-sm text-primary underline">Retry</button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Server Overview</h2>
      {stats && <OverviewCards stats={stats} />}
    </div>
  );
}
