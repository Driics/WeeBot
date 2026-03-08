"use client";

import { useEffect, useRef, useState } from "react";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { CaseFilterParams } from "@/types";

interface Props {
  filters: CaseFilterParams;
  onChange: (filters: CaseFilterParams) => void;
}

export function CaseFilters({ filters, onChange }: Props) {
  const [search, setSearch] = useState(filters.search ?? "");
  const [moderatorId, setModeratorId] = useState(filters.moderatorId ?? "");
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => {
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      onChange({
        ...filters,
        search: search || undefined,
        moderatorId: moderatorId || undefined,
        page: 0,
      });
    }, 300);
    return () => clearTimeout(debounceRef.current);
    // Only debounce on local input changes, not on external filter changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, moderatorId]);

  return (
    <div className="flex flex-wrap items-center gap-3">
      <Select
        value={filters.type ?? "all"}
        onValueChange={(v) =>
          onChange({ ...filters, type: v === "all" ? undefined : v, page: 0 })
        }
      >
        <SelectTrigger className="w-[140px]">
          <SelectValue placeholder="All Types" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All Types</SelectItem>
          <SelectItem value="warn">Warn</SelectItem>
          <SelectItem value="mute">Mute</SelectItem>
          <SelectItem value="kick">Kick</SelectItem>
          <SelectItem value="ban">Ban</SelectItem>
          <SelectItem value="unban">Unban</SelectItem>
          <SelectItem value="unmute">Unmute</SelectItem>
        </SelectContent>
      </Select>

      <Input
        placeholder="Search target..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="w-[200px]"
      />

      <Input
        placeholder="Moderator ID..."
        value={moderatorId}
        onChange={(e) => setModeratorId(e.target.value)}
        className="w-[180px]"
      />
    </div>
  );
}
