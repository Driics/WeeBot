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
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Plus, Trash2, Edit, Send, Save, Users } from "lucide-react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { RoleItemEditor } from "./RoleItemEditor";
import { RoleGroupEditor } from "./RoleGroupEditor";
import type {
  ReactionRoleMenuResponse,
  CreateReactionRoleMenuRequest,
  UpdateReactionRoleMenuRequest,
  ReactionRoleMenuItemResponse,
  ReactionRoleGroupResponse,
  GuildRole,
  ReactionRoleMenuType,
} from "@/types";

interface Props {
  guildId: string;
  menu?: ReactionRoleMenuResponse;
  roles: GuildRole[];
  groups: ReactionRoleGroupResponse[];
  onSave: () => void;
  onCancel: () => void;
  onRefreshGroups: () => void;
}

export function RoleMenuBuilder({
  guildId,
  menu,
  roles,
  groups,
  onSave,
  onCancel,
  onRefreshGroups,
}: Props) {
  const [state, setState] = useState({
    title: menu?.title ?? "",
    description: menu?.description ?? "",
    menuType: (menu?.menuType ?? "BUTTONS") as ReactionRoleMenuType,
    channelId: menu?.channelId ?? "",
  });
  const [items, setItems] = useState<ReactionRoleMenuItemResponse[]>(
    menu?.items ?? []
  );
  const [editingItem, setEditingItem] = useState<ReactionRoleMenuItemResponse | null>(
    null
  );
  const [isAddingItem, setIsAddingItem] = useState(false);
  const [isManagingGroups, setIsManagingGroups] = useState(false);
  const [saving, setSaving] = useState(false);
  const [posting, setPosting] = useState(false);

  async function handleSaveMenu() {
    if (!state.title.trim()) {
      toast.error("Please enter a menu title");
      return;
    }
    if (!state.channelId.trim()) {
      toast.error("Please enter a channel ID");
      return;
    }

    setSaving(true);
    try {
      if (menu) {
        // Update existing menu
        const updateData: UpdateReactionRoleMenuRequest = {
          title: state.title.trim(),
          description: state.description.trim() || undefined,
          menuType: state.menuType,
          channelId: state.channelId.trim(),
        };
        await api.updateReactionRoleMenu(guildId, menu.id, updateData);
        toast.success("Role menu updated successfully");
      } else {
        // Create new menu
        const createData: CreateReactionRoleMenuRequest = {
          title: state.title.trim(),
          description: state.description.trim() || undefined,
          menuType: state.menuType,
          channelId: state.channelId.trim(),
        };
        await api.createReactionRoleMenu(guildId, createData);
        toast.success("Role menu created successfully");
      }
      onSave();
    } catch {
      toast.error(
        menu ? "Failed to update role menu" : "Failed to create role menu"
      );
    } finally {
      setSaving(false);
    }
  }

  async function handlePostMenu() {
    if (!menu) {
      toast.error("Please save the menu before posting");
      return;
    }
    if (items.length === 0) {
      toast.error("Please add at least one menu item before posting");
      return;
    }

    setPosting(true);
    try {
      const response = await api.postReactionRoleMenu(guildId, menu.id);
      if (response.success) {
        toast.success("Role menu posted to channel successfully");
        onSave();
      } else {
        toast.error(response.message || "Failed to post role menu");
      }
    } catch {
      toast.error("Failed to post role menu to Discord");
    } finally {
      setPosting(false);
    }
  }

  async function handleDeleteItem(itemId: number) {
    if (!menu) return;

    try {
      await api.deleteReactionRoleMenuItem(guildId, itemId);
      setItems((prev) => prev.filter((item) => item.id !== itemId));
      toast.success("Menu item deleted successfully");
    } catch {
      toast.error("Failed to delete menu item");
    }
  }

  function handleItemSaved() {
    setEditingItem(null);
    setIsAddingItem(false);
    onSave();
  }

  function handleGroupSaved() {
    setIsManagingGroups(false);
    onRefreshGroups();
    toast.success("Please refresh to see updated groups");
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>{menu ? "Edit Role Menu" : "Create Role Menu"}</CardTitle>
          <CardDescription>
            Configure the menu properties and add role items
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <Label htmlFor="title">Menu Title *</Label>
            <Input
              id="title"
              value={state.title}
              onChange={(e) => setState((s) => ({ ...s, title: e.target.value }))}
              placeholder="e.g., Choose Your Roles"
              maxLength={100}
            />
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
              placeholder="Optional description for this role menu"
              maxLength={500}
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="menuType">Menu Type</Label>
              <Select
                value={state.menuType}
                onValueChange={(v) =>
                  setState((s) => ({ ...s, menuType: v as ReactionRoleMenuType }))
                }
              >
                <SelectTrigger id="menuType">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="REACTIONS">Reactions Only</SelectItem>
                  <SelectItem value="BUTTONS">Buttons Only</SelectItem>
                  <SelectItem value="BOTH">Both Reactions & Buttons</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="channelId">Channel ID *</Label>
              <Input
                id="channelId"
                value={state.channelId}
                onChange={(e) =>
                  setState((s) => ({ ...s, channelId: e.target.value }))
                }
                placeholder="123456789012345678"
                className="font-mono text-sm"
              />
            </div>
          </div>

          <div className="flex gap-2">
            <Button onClick={handleSaveMenu} disabled={saving}>
              <Save className="h-4 w-4 mr-2" />
              {saving ? "Saving..." : menu ? "Update Menu" : "Create Menu"}
            </Button>
            {menu && (
              <Button
                onClick={handlePostMenu}
                disabled={posting || items.length === 0}
                variant="outline"
              >
                <Send className="h-4 w-4 mr-2" />
                {posting ? "Posting..." : "Post to Channel"}
              </Button>
            )}
            <Button variant="outline" onClick={onCancel}>
              Cancel
            </Button>
          </div>
        </CardContent>
      </Card>

      {menu && (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Menu Items</CardTitle>
                <CardDescription>
                  Add and configure role items for this menu
                </CardDescription>
              </div>
              <div className="flex gap-2">
                <Dialog open={isManagingGroups} onOpenChange={setIsManagingGroups}>
                  <DialogTrigger asChild>
                    <Button variant="outline" size="sm">
                      <Users className="h-4 w-4 mr-2" />
                      Manage Groups
                    </Button>
                  </DialogTrigger>
                  <DialogContent className="max-w-2xl">
                    <DialogHeader>
                      <DialogTitle>Manage Role Groups</DialogTitle>
                    </DialogHeader>
                    <div className="py-4">
                      <RoleGroupEditor
                        guildId={guildId}
                        onSave={handleGroupSaved}
                        onCancel={() => setIsManagingGroups(false)}
                      />
                    </div>
                  </DialogContent>
                </Dialog>
                <Button size="sm" onClick={() => setIsAddingItem(true)}>
                  <Plus className="h-4 w-4 mr-2" />
                  Add Item
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            {items.length === 0 ? (
              <p className="text-center text-muted-foreground py-8">
                No menu items yet. Click "Add Item" to get started.
              </p>
            ) : (
              <div className="space-y-2">
                {items
                  .sort((a, b) => a.displayOrder - b.displayOrder)
                  .map((item) => {
                    const role = roles.find((r) => r.id === item.roleId);
                    const group = groups.find((g) => g.id === item.groupId);
                    return (
                      <div
                        key={item.id}
                        className="flex items-center justify-between rounded-md border px-4 py-3 hover:bg-accent/50"
                      >
                        <div className="flex items-center gap-3 flex-1">
                          {item.emoji && (
                            <span className="text-xl">{item.emoji}</span>
                          )}
                          <div className="flex-1">
                            <div className="font-medium">{item.label}</div>
                            <div className="text-sm text-muted-foreground">
                              Role: {role?.name ?? item.roleId}
                              {group && ` • Group: ${group.name}`}
                              {item.toggleable && " • Toggleable"}
                            </div>
                          </div>
                          <div className="flex gap-2">
                            {item.requiredRoleIds &&
                              item.requiredRoleIds.length > 0 && (
                                <Badge variant="outline" className="text-xs">
                                  {item.requiredRoleIds.length} required
                                </Badge>
                              )}
                            <Badge
                              variant="outline"
                              className={
                                item.active
                                  ? "bg-green-500/20 text-green-400 border-green-500/30"
                                  : "bg-gray-500/20 text-gray-400 border-gray-500/30"
                              }
                            >
                              {item.active ? "Active" : "Inactive"}
                            </Badge>
                          </div>
                        </div>
                        <div className="flex gap-2 ml-4">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setEditingItem(item)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDeleteItem(item.id)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    );
                  })}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Add/Edit Item Sheet */}
      <Sheet
        open={isAddingItem || !!editingItem}
        onOpenChange={(open) => {
          if (!open) {
            setIsAddingItem(false);
            setEditingItem(null);
          }
        }}
      >
        <SheetContent className="w-full sm:max-w-2xl overflow-y-auto">
          <SheetHeader>
            <SheetTitle>
              {editingItem ? "Edit Menu Item" : "Add Menu Item"}
            </SheetTitle>
          </SheetHeader>
          <div className="py-6">
            {menu && (
              <RoleItemEditor
                guildId={guildId}
                menuId={menu.id}
                item={editingItem ?? undefined}
                roles={roles}
                groups={groups}
                onSave={handleItemSaved}
                onCancel={() => {
                  setIsAddingItem(false);
                  setEditingItem(null);
                }}
              />
            )}
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
