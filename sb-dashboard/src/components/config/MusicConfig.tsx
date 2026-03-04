"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Slider } from "@/components/ui/slider";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "sonner";
import { api } from "@/lib/api";
import type { MusicConfig as MusicConfigType, GuildRole } from "@/types";

interface Props {
  guildId: string;
  config: MusicConfigType;
  roles: GuildRole[];
}

export function MusicConfig({ guildId, config, roles }: Props) {
  const [state, setState] = useState(config);
  const [saving, setSaving] = useState(false);

  async function handleSave() {
    setSaving(true);
    try {
      await api.updateGuildConfig(guildId, { music: state });
      toast.success("Music settings saved");
    } catch {
      toast.error("Failed to save music settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label>Default Volume: {state.defaultVolume}%</Label>
        <Slider
          value={[state.defaultVolume]}
          onValueChange={([v]) =>
            setState((s) => ({ ...s, defaultVolume: v }))
          }
          min={1}
          max={100}
          step={1}
          className="max-w-sm"
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="djrole">DJ Role</Label>
        <Select
          value={state.djRoleId ?? "none"}
          onValueChange={(v) =>
            setState((s) => ({
              ...s,
              djRoleId: v === "none" ? null : v,
            }))
          }
        >
          <SelectTrigger id="djrole" className="w-[200px]">
            <SelectValue placeholder="None" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="none">None</SelectItem>
            {roles.map((role) => (
              <SelectItem key={role.id} value={role.id}>
                {role.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label htmlFor="maxqueue">Max Queue Size</Label>
        <Input
          id="maxqueue"
          type="number"
          value={state.maxQueueSize}
          onChange={(e) =>
            setState((s) => ({
              ...s,
              maxQueueSize: parseInt(e.target.value) || 0,
            }))
          }
          className="max-w-[120px]"
        />
      </div>

      <div className="flex items-center justify-between max-w-sm">
        <Label htmlFor="247">24/7 Mode</Label>
        <Switch
          id="247"
          checked={state.twentyFourSevenEnabled}
          onCheckedChange={(v) =>
            setState((s) => ({ ...s, twentyFourSevenEnabled: v }))
          }
        />
      </div>

      <Button onClick={handleSave} disabled={saving}>
        {saving ? "Saving..." : "Save Changes"}
      </Button>
    </div>
  );
}
