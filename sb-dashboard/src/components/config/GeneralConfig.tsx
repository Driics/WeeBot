"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
import type { GeneralConfig as GeneralConfigType } from "@/types";

interface Props {
  guildId: string;
  config: GeneralConfigType;
}

export function GeneralConfig({ guildId, config }: Props) {
  const [language, setLanguage] = useState(config.language);
  const [botNickname, setBotNickname] = useState(config.botNickname);
  const [saving, setSaving] = useState(false);

  async function handleSave() {
    setSaving(true);
    try {
      await api.updateGuildConfig(guildId, {
        general: { language, botNickname },
      });
      toast.success("General settings saved");
    } catch {
      toast.error("Failed to save general settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="language">Language</Label>
        <Select value={language} onValueChange={setLanguage}>
          <SelectTrigger id="language" className="w-[200px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="en">English</SelectItem>
            <SelectItem value="ru">Russian</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label htmlFor="nickname">Bot Nickname</Label>
        <Input
          id="nickname"
          value={botNickname}
          onChange={(e) => setBotNickname(e.target.value)}
          placeholder="SableBot"
          className="max-w-sm"
        />
      </div>

      <Button onClick={handleSave} disabled={saving}>
        {saving ? "Saving..." : "Save Changes"}
      </Button>
    </div>
  );
}
