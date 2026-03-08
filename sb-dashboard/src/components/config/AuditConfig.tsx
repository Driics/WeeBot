"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "sonner";
import { api } from "@/lib/api";
import type { AuditConfig as AuditConfigType, GuildChannel } from "@/types";

const AUDIT_EVENTS = [
  { id: "message_delete", label: "Message Delete" },
  { id: "message_edit", label: "Message Edit" },
  { id: "member_join", label: "Member Join" },
  { id: "member_leave", label: "Member Leave" },
  { id: "member_ban", label: "Member Ban" },
  { id: "member_unban", label: "Member Unban" },
  { id: "role_change", label: "Role Change" },
  { id: "channel_change", label: "Channel Change" },
  { id: "voice_join", label: "Voice Join" },
  { id: "voice_leave", label: "Voice Leave" },
];

interface Props {
  guildId: string;
  config: AuditConfigType;
  channels: GuildChannel[];
}

export function AuditConfig({ guildId, config, channels }: Props) {
  const [state, setState] = useState(config);
  const [saving, setSaving] = useState(false);

  function toggleEvent(eventId: string) {
    setState((s) => ({
      ...s,
      loggedEvents: s.loggedEvents.includes(eventId)
        ? s.loggedEvents.filter((e) => e !== eventId)
        : [...s.loggedEvents, eventId],
    }));
  }

  async function handleSave() {
    setSaving(true);
    try {
      await api.updateGuildConfig(guildId, { audit: state });
      toast.success("Audit settings saved");
    } catch {
      toast.error("Failed to save audit settings");
    } finally {
      setSaving(false);
    }
  }

  const textChannels = channels.filter((c) => c.type === "text");

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="logchannel">Log Channel</Label>
        <Select
          value={state.logChannelId ?? "none"}
          onValueChange={(v) =>
            setState((s) => ({
              ...s,
              logChannelId: v === "none" ? null : v,
            }))
          }
        >
          <SelectTrigger id="logchannel" className="w-[240px]">
            <SelectValue placeholder="None" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="none">None</SelectItem>
            {textChannels.map((ch) => (
              <SelectItem key={ch.id} value={ch.id}>
                #{ch.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-3">
        <Label>Events to Log</Label>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {AUDIT_EVENTS.map((event) => (
            <label
              key={event.id}
              className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm cursor-pointer hover:bg-accent"
            >
              <input
                type="checkbox"
                checked={state.loggedEvents.includes(event.id)}
                onChange={() => toggleEvent(event.id)}
                className="h-4 w-4 rounded border-input"
              />
              {event.label}
            </label>
          ))}
        </div>
      </div>

      <Button onClick={handleSave} disabled={saving}>
        {saving ? "Saving..." : "Save Changes"}
      </Button>
    </div>
  );
}
