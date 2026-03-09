"use client";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ReactionRoleMenuListResponse, ReactionRoleMenuResponse } from "@/types";

interface Props {
  data: ReactionRoleMenuListResponse;
  onPageChange: (page: number) => void;
  onSelectMenu: (menu: ReactionRoleMenuResponse) => void;
}

const menuTypeColors: Record<string, string> = {
  REACTIONS: "bg-blue-500/20 text-blue-400 border-blue-500/30",
  BUTTONS: "bg-purple-500/20 text-purple-400 border-purple-500/30",
  BOTH: "bg-green-500/20 text-green-400 border-green-500/30",
};

export function RoleMenuList({ data, onPageChange, onSelectMenu }: Props) {
  const totalPages = Math.ceil(data.total / data.size);

  return (
    <div className="space-y-4">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[200px]">Title</TableHead>
              <TableHead className="w-[120px]">Type</TableHead>
              <TableHead className="w-[140px]">Channel</TableHead>
              <TableHead className="w-[100px]">Items</TableHead>
              <TableHead className="w-[100px]">Status</TableHead>
              <TableHead className="w-[140px]">Created</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.menus.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No role menus found
                </TableCell>
              </TableRow>
            ) : (
              data.menus.map((menu) => (
                <TableRow
                  key={menu.id}
                  className="cursor-pointer hover:bg-accent/50"
                  onClick={() => onSelectMenu(menu)}
                >
                  <TableCell className="font-medium truncate max-w-[200px]">
                    {menu.title}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className={menuTypeColors[menu.menuType] ?? ""}>
                      {menu.menuType}
                    </Badge>
                  </TableCell>
                  <TableCell className="font-mono text-sm text-muted-foreground truncate">
                    #{menu.channelId.slice(-6)}
                  </TableCell>
                  <TableCell className="text-center">
                    {menu.items.length}
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant="outline"
                      className={
                        menu.active
                          ? "bg-green-500/20 text-green-400 border-green-500/30"
                          : "bg-gray-500/20 text-gray-400 border-gray-500/30"
                      }
                    >
                      {menu.active ? "Active" : "Inactive"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {new Date(menu.createdAt).toLocaleDateString()}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">
            Page {data.page + 1} of {totalPages} ({data.total} total)
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              aria-label="Previous page"
              disabled={data.page === 0}
              onClick={() => onPageChange(data.page - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              aria-label="Next page"
              disabled={data.page >= totalPages - 1}
              onClick={() => onPageChange(data.page + 1)}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
