"use client";

import { useEffect, useState } from "react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { CreateFeedRequest, FeedResponse, FeedType, GuildChannel } from "@/types";

interface Props {
  initialData?: FeedResponse;
  channels: GuildChannel[];
  onSubmit: (data: CreateFeedRequest) => void;
  onCancel: () => void;
  isSubmitting?: boolean;
}

export function FeedForm({ initialData, channels, onSubmit, onCancel, isSubmitting }: Props) {
  const [feedType, setFeedType] = useState<FeedType>(initialData?.feedType ?? "REDDIT");
  const [targetIdentifier, setTargetIdentifier] = useState(initialData?.targetIdentifier ?? "");
  const [targetChannelId, setTargetChannelId] = useState(initialData?.targetChannelId ?? "");
  const [checkIntervalMinutes, setCheckIntervalMinutes] = useState(
    initialData?.checkIntervalMinutes?.toString() ?? "15"
  );
  const [enabled, setEnabled] = useState(initialData?.enabled ?? true);
  const [embedConfig, setEmbedConfig] = useState(
    initialData?.embedConfig ? JSON.stringify(initialData.embedConfig, null, 2) : ""
  );
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!targetIdentifier.trim()) {
      newErrors.targetIdentifier = "Target identifier is required";
    }

    if (!targetChannelId) {
      newErrors.targetChannelId = "Target channel is required";
    }

    const intervalNum = parseInt(checkIntervalMinutes, 10);
    if (isNaN(intervalNum) || intervalNum < 1) {
      newErrors.checkIntervalMinutes = "Interval must be at least 1 minute";
    }

    if (embedConfig.trim()) {
      try {
        JSON.parse(embedConfig);
      } catch {
        newErrors.embedConfig = "Invalid JSON format";
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (!validate()) {
      return;
    }

    const data: CreateFeedRequest = {
      feedType,
      targetIdentifier: targetIdentifier.trim(),
      targetChannelId,
      checkIntervalMinutes: parseInt(checkIntervalMinutes, 10),
      enabled,
    };

    if (embedConfig.trim()) {
      try {
        data.embedConfig = JSON.parse(embedConfig);
      } catch {
        // Already validated, should not happen
      }
    }

    onSubmit(data);
  };

  const getPlaceholder = (type: FeedType): string => {
    switch (type) {
      case "REDDIT":
        return "subreddit (e.g., programming)";
      case "TWITCH":
        return "username (e.g., shroud)";
      case "YOUTUBE":
        return "channel ID (e.g., UC...)";
      default:
        return "";
    }
  };

  useEffect(() => {
    // Clear target identifier error when user starts typing
    if (errors.targetIdentifier && targetIdentifier.trim()) {
      setErrors((prev) => ({ ...prev, targetIdentifier: "" }));
    }
  }, [targetIdentifier, errors.targetIdentifier]);

  useEffect(() => {
    // Clear channel error when user selects a channel
    if (errors.targetChannelId && targetChannelId) {
      setErrors((prev) => ({ ...prev, targetChannelId: "" }));
    }
  }, [targetChannelId, errors.targetChannelId]);

  const textChannels = channels.filter((ch) => ch.type === "text");

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="feedType">Feed Type</Label>
        <Select
          value={feedType}
          onValueChange={(v) => setFeedType(v as FeedType)}
          disabled={!!initialData}
        >
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Select feed type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="REDDIT">Reddit</SelectItem>
            <SelectItem value="TWITCH">Twitch</SelectItem>
            <SelectItem value="YOUTUBE">YouTube</SelectItem>
          </SelectContent>
        </Select>
        {initialData && (
          <p className="text-xs text-muted-foreground">Feed type cannot be changed after creation</p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="targetIdentifier">Target Identifier</Label>
        <Input
          id="targetIdentifier"
          placeholder={getPlaceholder(feedType)}
          value={targetIdentifier}
          onChange={(e) => setTargetIdentifier(e.target.value)}
          aria-invalid={!!errors.targetIdentifier}
        />
        {errors.targetIdentifier && (
          <p className="text-xs text-destructive">{errors.targetIdentifier}</p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="targetChannelId">Target Channel</Label>
        <Select value={targetChannelId} onValueChange={setTargetChannelId}>
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Select a channel" />
          </SelectTrigger>
          <SelectContent>
            {textChannels.length === 0 ? (
              <div className="px-2 py-1.5 text-sm text-muted-foreground">
                No text channels available
              </div>
            ) : (
              textChannels.map((channel) => (
                <SelectItem key={channel.id} value={channel.id}>
                  #{channel.name}
                </SelectItem>
              ))
            )}
          </SelectContent>
        </Select>
        {errors.targetChannelId && (
          <p className="text-xs text-destructive">{errors.targetChannelId}</p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="checkIntervalMinutes">Check Interval (minutes)</Label>
        <Input
          id="checkIntervalMinutes"
          type="number"
          min="1"
          placeholder="15"
          value={checkIntervalMinutes}
          onChange={(e) => setCheckIntervalMinutes(e.target.value)}
          aria-invalid={!!errors.checkIntervalMinutes}
        />
        {errors.checkIntervalMinutes && (
          <p className="text-xs text-destructive">{errors.checkIntervalMinutes}</p>
        )}
        <p className="text-xs text-muted-foreground">
          How often to check for new content (minimum 1 minute)
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="embedConfig">Embed Config (optional JSON)</Label>
        <textarea
          id="embedConfig"
          className="w-full min-h-[120px] rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-destructive/20 dark:bg-input/30"
          placeholder='{"title": "Custom Title", "color": "#FF5733"}'
          value={embedConfig}
          onChange={(e) => setEmbedConfig(e.target.value)}
          aria-invalid={!!errors.embedConfig}
        />
        {errors.embedConfig && (
          <p className="text-xs text-destructive">{errors.embedConfig}</p>
        )}
        <p className="text-xs text-muted-foreground">
          Custom Discord embed configuration (title, description, color, etc.)
        </p>
      </div>

      <div className="flex items-center gap-3">
        <Switch
          id="enabled"
          checked={enabled}
          onCheckedChange={setEnabled}
        />
        <Label htmlFor="enabled" className="cursor-pointer">
          {enabled ? "Enabled" : "Disabled"}
        </Label>
      </div>

      <div className="flex justify-end gap-3 pt-2">
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={isSubmitting}
        >
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving..." : initialData ? "Update Feed" : "Create Feed"}
        </Button>
      </div>
    </form>
  );
}
