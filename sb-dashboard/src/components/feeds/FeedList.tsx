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
import type { FeedListResponse, FeedResponse } from "@/types";

interface Props {
  data: FeedListResponse;
  onPageChange: (page: number) => void;
  onSelectFeed: (feed: FeedResponse) => void;
  channelNames?: Map<string, string>;
}

const typeColors: Record<string, string> = {
  REDDIT: "bg-orange-500/20 text-orange-400 border-orange-500/30",
  TWITCH: "bg-purple-500/20 text-purple-400 border-purple-500/30",
  YOUTUBE: "bg-red-500/20 text-red-400 border-red-500/30",
};

export function FeedList({ data, onPageChange, onSelectFeed, channelNames }: Props) {
  return (
    <div className="space-y-4">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[100px]">Type</TableHead>
              <TableHead>Target</TableHead>
              <TableHead>Channel</TableHead>
              <TableHead className="w-[120px]">Interval</TableHead>
              <TableHead className="w-[100px]">Status</TableHead>
              <TableHead className="w-[140px]">Last Check</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.feeds.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center text-muted-foreground">
                  No feeds configured
                </TableCell>
              </TableRow>
            ) : (
              data.feeds.map((feed) => (
                <TableRow
                  key={feed.id}
                  className="cursor-pointer hover:bg-accent/50"
                  onClick={() => onSelectFeed(feed)}
                >
                  <TableCell>
                    <Badge variant="outline" className={typeColors[feed.feedType] ?? ""}>
                      {feed.feedType}
                    </Badge>
                  </TableCell>
                  <TableCell className="truncate max-w-[200px]">
                    {feed.targetIdentifier}
                  </TableCell>
                  <TableCell className="truncate max-w-[160px]">
                    {channelNames?.get(feed.targetChannelId) ?? `#${feed.targetChannelId}`}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {feed.checkIntervalMinutes}m
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant="outline"
                      className={
                        feed.enabled
                          ? "bg-green-500/20 text-green-400 border-green-500/30"
                          : "bg-gray-500/20 text-gray-400 border-gray-500/30"
                      }
                    >
                      {feed.enabled ? "Active" : "Disabled"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {feed.lastCheckTime
                      ? new Date(feed.lastCheckTime).toLocaleDateString()
                      : "-"}
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {data.total > data.size && (
        <div className="flex items-center justify-between">
          <span className="text-sm text-muted-foreground">
            Page {data.page + 1} of {Math.ceil(data.total / data.size)} ({data.total} total)
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
              disabled={data.page >= Math.ceil(data.total / data.size) - 1}
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
