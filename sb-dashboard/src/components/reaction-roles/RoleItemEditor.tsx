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
import type {
  ReactionRoleMenuItemResponse,
  CreateReactionRoleMenuItemRequest,
  UpdateReactionRoleMenuItemRequest,
  GuildRole,
  ReactionRoleGroupResponse,
} from "@/types";

interface Props {
  guildId: string;
  menuId: number;
  item?: ReactionRoleMenuItemResponse;
  roles: GuildRole[];
  groups: ReactionRoleGroupResponse[];
  onSave: () => void;
  onCancel: () => void;
}

export function RoleItemEditor({
  guildId,
  menuId,
  item,
  roles,
  groups,
  onSave,
  onCancel,
}: Props) {
  const [state, setState] = useState({
    roleId: item?.roleId ?? "",
    emoji: item?.emoji ?? "",
    label: item?.label ?? "",
    description: item?.description ?? "",
    displayOrder: item?.displayOrder ?? 0,
    toggleable: item?.toggleable ?? true,
    groupId: item?.groupId ?? null as number | null,
    requiredRoleIds: item?.requiredRoleIds ?? [] as string[],
  });
  const [saving, setSaving] = useState(false);

  function toggleRequiredRole(roleId: string) {
    setState((s) => ({
      ...s,
      requiredRoleIds: s.requiredRoleIds.includes(roleId)
        ? s.requiredRoleIds.filter((id) => id !== roleId)
        : [...s.requiredRoleIds, roleId],
    }));
  }

  async function handleSave() {
    if (!state.roleId) {
      toast.error("Please select a role");
      return;
    }
    if (!state.label.trim()) {
      toast.error("Please enter a label");
      return;
    }

    setSaving(true);
    try {
      if (item) {
        // Update existing item
        const updateData: UpdateReactionRoleMenuItemRequest = {
          roleId: state.roleId,
          emoji: state.emoji || undefined,
          label: state.label,
          description: state.description || undefined,
          displayOrder: state.displayOrder,
          toggleable: state.toggleable,
          groupId: state.groupId ?? undefined,
          requiredRoleIds:
            state.requiredRoleIds.length > 0
              ? state.requiredRoleIds
              : undefined,
        };
        await api.updateReactionRoleMenuItem(guildId, item.id, updateData);
        toast.success("Role item updated successfully");
      } else {
        // Create new item
        const createData: CreateReactionRoleMenuItemRequest = {
          roleId: state.roleId,
          emoji: state.emoji || undefined,
          label: state.label,
          description: state.description || undefined,
          displayOrder: state.displayOrder,
          toggleable: state.toggleable,
          groupId: state.groupId ?? undefined,
          requiredRoleIds:
            state.requiredRoleIds.length > 0
              ? state.requiredRoleIds
              : undefined,
        };
        await api.createReactionRoleMenuItem(guildId, menuId, createData);
        toast.success("Role item created successfully");
      }
      onSave();
    } catch {
      toast.error(
        item ? "Failed to update role item" : "Failed to create role item"
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="role">Role *</Label>
        <Select
          value={state.roleId}
          onValueChange={(v) => setState((s) => ({ ...s, roleId: v }))}
        >
          <SelectTrigger id="role" className="w-full">
            <SelectValue placeholder="Select a role" />
          </SelectTrigger>
          <SelectContent>
            {roles.map((role) => (
              <SelectItem key={role.id} value={role.id}>
                {role.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="space-y-2">
        <Label htmlFor="label">Label *</Label>
        <Input
          id="label"
          value={state.label}
          onChange={(e) => setState((s) => ({ ...s, label: e.target.value }))}
          placeholder="e.g., Get Red Role"
          maxLength={100}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="emoji">Emoji</Label>
        <Input
          id="emoji"
          value={state.emoji}
          onChange={(e) => setState((s) => ({ ...s, emoji: e.target.value }))}
          placeholder="e.g., 🔴 or :red_circle:"
          maxLength={50}
        />
        <p className="text-xs text-muted-foreground">
          Unicode emoji (🔴) or Discord emoji name (:custom_emoji:)
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">Description</Label>
        <textarea
          id="description"
          className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          value={state.description}
          onChange={(e) =>
            setState((s) => ({ ...s, description: e.target.value }))
          }
          placeholder="Optional description for this role"
          maxLength={200}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="displayOrder">Display Order</Label>
          <Input
            id="displayOrder"
            type="number"
            value={state.displayOrder}
            onChange={(e) =>
              setState((s) => ({
                ...s,
                displayOrder: parseInt(e.target.value, 10) || 0,
              }))
            }
            min={0}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="group">Role Group</Label>
          <Select
            value={state.groupId?.toString() ?? "none"}
            onValueChange={(v) =>
              setState((s) => ({
                ...s,
                groupId: v === "none" ? null : parseInt(v, 10),
              }))
            }
          >
            <SelectTrigger id="group" className="w-full">
              <SelectValue placeholder="None" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">None</SelectItem>
              {groups.map((group) => (
                <SelectItem key={group.id} value={group.id.toString()}>
                  {group.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="space-y-3">
        <label className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm cursor-pointer hover:bg-accent">
          <input
            type="checkbox"
            checked={state.toggleable}
            onChange={(e) =>
              setState((s) => ({ ...s, toggleable: e.target.checked }))
            }
            className="h-4 w-4 rounded border-input"
          />
          <div>
            <div className="font-medium">Toggleable</div>
            <div className="text-xs text-muted-foreground">
              Users can remove the role by clicking again
            </div>
          </div>
        </label>
      </div>

      <div className="space-y-3">
        <Label>Required Roles</Label>
        <p className="text-xs text-muted-foreground">
          Users must have these roles to obtain this role
        </p>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {roles.map((role) => (
            <label
              key={role.id}
              className="flex items-center gap-2 rounded-md border px-3 py-2 text-sm cursor-pointer hover:bg-accent"
            >
              <input
                type="checkbox"
                checked={state.requiredRoleIds.includes(role.id)}
                onChange={() => toggleRequiredRole(role.id)}
                className="h-4 w-4 rounded border-input"
              />
              {role.name}
            </label>
          ))}
        </div>
      </div>

      <div className="flex gap-2 pt-4">
        <Button onClick={handleSave} disabled={saving}>
          {saving ? "Saving..." : item ? "Update Item" : "Create Item"}
        </Button>
        <Button variant="outline" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
