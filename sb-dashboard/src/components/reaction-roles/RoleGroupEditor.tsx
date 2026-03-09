"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";
import { api } from "@/lib/api";
import type {
  ReactionRoleGroupResponse,
  CreateReactionRoleGroupRequest,
  UpdateReactionRoleGroupRequest,
} from "@/types";

interface Props {
  guildId: string;
  group?: ReactionRoleGroupResponse;
  onSave: () => void;
  onCancel: () => void;
}

export function RoleGroupEditor({ guildId, group, onSave, onCancel }: Props) {
  const [name, setName] = useState(group?.name ?? "");
  const [description, setDescription] = useState(group?.description ?? "");
  const [saving, setSaving] = useState(false);

  async function handleSave() {
    if (!name.trim()) {
      toast.error("Please enter a group name");
      return;
    }

    setSaving(true);
    try {
      if (group) {
        // Update existing group
        const updateData: UpdateReactionRoleGroupRequest = {
          name: name.trim(),
          description: description.trim() || undefined,
        };
        await api.updateReactionRoleGroup(guildId, group.id, updateData);
        toast.success("Role group updated successfully");
      } else {
        // Create new group
        const createData: CreateReactionRoleGroupRequest = {
          name: name.trim(),
          description: description.trim() || undefined,
        };
        await api.createReactionRoleGroup(guildId, createData);
        toast.success("Role group created successfully");
      }
      onSave();
    } catch {
      toast.error(
        group ? "Failed to update role group" : "Failed to create role group"
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="name">Group Name *</Label>
        <Input
          id="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="e.g., Color Roles"
          maxLength={100}
          className="max-w-sm"
        />
        <p className="text-xs text-muted-foreground">
          A name to identify this exclusive role group
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">Description</Label>
        <textarea
          id="description"
          className="flex min-h-[80px] w-full max-w-lg rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Optional description for this group"
          maxLength={200}
        />
        <p className="text-xs text-muted-foreground">
          Explain what this group is for (e.g., "Choose one color role")
        </p>
      </div>

      <div className="flex gap-2">
        <Button onClick={handleSave} disabled={saving}>
          {saving ? "Saving..." : group ? "Update Group" : "Create Group"}
        </Button>
        <Button variant="outline" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
