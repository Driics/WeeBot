"use client";

import { use, useEffect, useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { api } from "@/lib/api";
import type { GuildConfig, GuildRole, GuildChannel } from "@/types";
import { GeneralConfig } from "@/components/config/GeneralConfig";
import { ModerationConfig } from "@/components/config/ModerationConfig";
import { MusicConfig } from "@/components/config/MusicConfig";
import { AuditConfig } from "@/components/config/AuditConfig";

export default function ConfigPage({
  params,
}: {
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);
  const [config, setConfig] = useState<GuildConfig | null>(null);
  const [roles, setRoles] = useState<GuildRole[]>([]);
  const [channels, setChannels] = useState<GuildChannel[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      api.getGuildConfig(guildId),
      api.getGuildRoles(guildId),
      api.getGuildChannels(guildId),
    ])
      .then(([cfg, r, ch]) => {
        setConfig(cfg);
        setRoles(r);
        setChannels(ch);
      })
      .catch(() => setError("Failed to load configuration"))
      .finally(() => setLoading(false));
  }, [guildId]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (error || !config) {
    return (
      <div className="flex flex-col items-center gap-4 py-12">
        <p className="text-destructive">{error ?? "Failed to load configuration"}</p>
        <button
          onClick={() => window.location.reload()}
          className="text-sm text-primary underline"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Server Configuration</h2>
      <Tabs defaultValue="general">
        <TabsList>
          <TabsTrigger value="general">General</TabsTrigger>
          <TabsTrigger value="moderation">Moderation</TabsTrigger>
          <TabsTrigger value="music">Music</TabsTrigger>
          <TabsTrigger value="audit">Audit</TabsTrigger>
        </TabsList>
        <TabsContent value="general" className="mt-6">
          <GeneralConfig guildId={guildId} config={config.general} />
        </TabsContent>
        <TabsContent value="moderation" className="mt-6">
          <ModerationConfig guildId={guildId} config={config.moderation} />
        </TabsContent>
        <TabsContent value="music" className="mt-6">
          <MusicConfig guildId={guildId} config={config.music} roles={roles} />
        </TabsContent>
        <TabsContent value="audit" className="mt-6">
          <AuditConfig guildId={guildId} config={config.audit} channels={channels} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
