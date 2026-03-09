"use client";

import { use, useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type {
  ReactionRoleMenuListResponse,
  ReactionRoleMenuResponse,
  ReactionRoleGroupListResponse,
  GuildRole,
} from "@/types";
import { RoleMenuList } from "@/components/reaction-roles/RoleMenuList";
import { RoleMenuBuilder } from "@/components/reaction-roles/RoleMenuBuilder";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";

export default function ReactionRolesPage({
  params,
}: {
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);
  const [page, setPage] = useState(0);
  const [menuData, setMenuData] = useState<ReactionRoleMenuListResponse | null>(null);
  const [roles, setRoles] = useState<GuildRole[]>([]);
  const [groupData, setGroupData] = useState<ReactionRoleGroupListResponse | null>(null);
  const [selectedMenu, setSelectedMenu] = useState<ReactionRoleMenuResponse | null>(null);
  const [showBuilder, setShowBuilder] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchMenus = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .getReactionRoleMenus(guildId, { page, size: 20 })
      .then(setMenuData)
      .catch((e) => setError(e.message || "Failed to load role menus"))
      .finally(() => setLoading(false));
  }, [guildId, page]);

  const fetchRoles = useCallback(() => {
    api
      .getGuildRoles(guildId)
      .then(setRoles)
      .catch(() => setRoles([]));
  }, [guildId]);

  const fetchGroups = useCallback(() => {
    api
      .getReactionRoleGroups(guildId, { page: 0, size: 100 })
      .then(setGroupData)
      .catch(() => setGroupData(null));
  }, [guildId]);

  useEffect(() => {
    fetchMenus();
  }, [fetchMenus]);

  useEffect(() => {
    fetchRoles();
    fetchGroups();
  }, [fetchRoles, fetchGroups]);

  function handleCreateNew() {
    setSelectedMenu(null);
    setShowBuilder(true);
  }

  function handleSelectMenu(menu: ReactionRoleMenuResponse) {
    setSelectedMenu(menu);
    setShowBuilder(true);
  }

  function handleSave() {
    setShowBuilder(false);
    setSelectedMenu(null);
    fetchMenus();
  }

  function handleCancel() {
    setShowBuilder(false);
    setSelectedMenu(null);
  }

  function handleRefreshGroups() {
    fetchGroups();
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="text-2xl font-bold">Reaction Roles</h2>
        {!showBuilder && (
          <Button onClick={handleCreateNew}>
            <Plus className="h-4 w-4 mr-2" />
            Create Role Menu
          </Button>
        )}
      </div>

      {showBuilder ? (
        <RoleMenuBuilder
          guildId={guildId}
          menu={selectedMenu ?? undefined}
          roles={roles}
          groups={groupData?.groups ?? []}
          onSave={handleSave}
          onCancel={handleCancel}
          onRefreshGroups={handleRefreshGroups}
        />
      ) : (
        <>
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : error ? (
            <div className="text-center py-8">
              <p className="text-destructive">{error}</p>
              <button onClick={fetchMenus} className="mt-2 text-sm text-primary underline">
                Retry
              </button>
            </div>
          ) : menuData ? (
            <RoleMenuList
              data={menuData}
              onPageChange={setPage}
              onSelectMenu={handleSelectMenu}
            />
          ) : (
            <p className="text-center text-muted-foreground">Failed to load role menus</p>
          )}
        </>
      )}
    </div>
  );
}
