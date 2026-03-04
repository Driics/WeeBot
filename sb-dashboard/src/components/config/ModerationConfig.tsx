"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Slider } from "@/components/ui/slider";
import { toast } from "sonner";
import { api } from "@/lib/api";
import type { ModerationConfig as ModerationConfigType } from "@/types";

interface Props {
  guildId: string;
  config: ModerationConfigType;
}

export function ModerationConfig({ guildId, config }: Props) {
  const [state, setState] = useState(config);
  const [saving, setSaving] = useState(false);

  type BooleanKeys<T> = { [K in keyof T]: T[K] extends boolean ? K : never }[keyof T];
  function toggle(key: BooleanKeys<ModerationConfigType>) {
    setState((s) => ({ ...s, [key]: !s[key] }));
  }

  async function handleSave() {
    setSaving(true);
    try {
      await api.updateGuildConfig(guildId, { moderation: state });
      toast.success("Moderation settings saved");
    } catch {
      toast.error("Failed to save moderation settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <Label htmlFor="automod">Auto-Moderation</Label>
          <Switch
            id="automod"
            checked={state.autoModEnabled}
            onCheckedChange={() => toggle("autoModEnabled")}
          />
        </div>

        <div className="flex items-center justify-between">
          <Label htmlFor="antispam">Anti-Spam</Label>
          <Switch
            id="antispam"
            checked={state.antiSpamEnabled}
            onCheckedChange={() => toggle("antiSpamEnabled")}
          />
        </div>

        {state.antiSpamEnabled && (
          <div className="space-y-2 pl-4">
            <Label>Spam Threshold: {state.antiSpamThreshold}</Label>
            <Slider
              value={[state.antiSpamThreshold]}
              onValueChange={([v]) =>
                setState((s) => ({ ...s, antiSpamThreshold: v }))
              }
              min={3}
              max={20}
              step={1}
              className="max-w-sm"
            />
          </div>
        )}

        <div className="flex items-center justify-between">
          <Label htmlFor="antilink">Anti-Link</Label>
          <Switch
            id="antilink"
            checked={state.antiLinkEnabled}
            onCheckedChange={() => toggle("antiLinkEnabled")}
          />
        </div>

        <div className="flex items-center justify-between">
          <Label htmlFor="antiprofanity">Anti-Profanity</Label>
          <Switch
            id="antiprofanity"
            checked={state.antiProfanityEnabled}
            onCheckedChange={() => toggle("antiProfanityEnabled")}
          />
        </div>

        <div className="flex items-center justify-between">
          <Label htmlFor="antiraid">Anti-Raid</Label>
          <Switch
            id="antiraid"
            checked={state.antiRaidEnabled}
            onCheckedChange={() => toggle("antiRaidEnabled")}
          />
        </div>

        {state.antiRaidEnabled && (
          <div className="space-y-2 pl-4">
            <Label>Raid Threshold: {state.antiRaidThreshold}</Label>
            <Slider
              value={[state.antiRaidThreshold]}
              onValueChange={([v]) =>
                setState((s) => ({ ...s, antiRaidThreshold: v }))
              }
              min={5}
              max={50}
              step={1}
              className="max-w-sm"
            />
          </div>
        )}

        <div className="space-y-2">
          <Label htmlFor="blacklist">Word Blacklist (one per line)</Label>
          <textarea
            id="blacklist"
            className="flex min-h-[100px] w-full max-w-sm rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            value={state.wordBlacklist.join("\n")}
            onChange={(e) =>
              setState((s) => ({
                ...s,
                wordBlacklist: e.target.value
                  .split("\n")
                  .filter((w) => w.trim() !== ""),
              }))
            }
          />
        </div>
      </div>

      <Button onClick={handleSave} disabled={saving}>
        {saving ? "Saving..." : "Save Changes"}
      </Button>
    </div>
  );
}
