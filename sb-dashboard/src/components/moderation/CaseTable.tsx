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
import type { CaseListResponse, CaseResponse } from "@/types";

interface Props {
  data: CaseListResponse;
  onPageChange: (page: number) => void;
  onSelectCase: (c: CaseResponse) => void;
}

const typeColors: Record<string, string> = {
  warn: "bg-yellow-500/20 text-yellow-400 border-yellow-500/30",
  mute: "bg-orange-500/20 text-orange-400 border-orange-500/30",
  kick: "bg-red-500/20 text-red-400 border-red-500/30",
  ban: "bg-red-700/20 text-red-300 border-red-700/30",
  unban: "bg-green-500/20 text-green-400 border-green-500/30",
  unmute: "bg-green-500/20 text-green-400 border-green-500/30",
};

export function CaseTable({ data, onPageChange, onSelectCase }: Props) {
  return (
    <div className="space-y-4">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[80px]">Case #</TableHead>
              <TableHead className="w-[100px]">Type</TableHead>
              <TableHead>Target</TableHead>
              <TableHead>Moderator</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead className="w-[140px]">Date</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.cases.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No cases found
                </TableCell>
              </TableRow>
            ) : (
              data.cases.map((c) => (
                <TableRow
                  key={c.id}
                  className="cursor-pointer hover:bg-accent/50"
                  onClick={() => onSelectCase(c)}
                >
                  <TableCell className="font-mono">#{c.caseNumber}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className={typeColors[c.type] ?? ""}>
                      {c.type}
                    </Badge>
                  </TableCell>
                  <TableCell className="truncate max-w-[160px]">
                    {c.targetUsername}
                  </TableCell>
                  <TableCell className="truncate max-w-[160px]">
                    {c.moderatorUsername}
                  </TableCell>
                  <TableCell className="truncate max-w-[200px] text-muted-foreground">
                    {c.reason ?? "-"}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {new Date(c.createdAt).toLocaleDateString()}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages} ({data.total} total)
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
              disabled={data.page >= data.totalPages - 1}
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
