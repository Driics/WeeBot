"use client";

import { useState } from "react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { Loader2, CheckCircle, XCircle, TestTube } from "lucide-react";
import { api } from "@/lib/api";
import type { FeedResponse } from "@/types";

interface Props {
  feed: FeedResponse | null;
  open: boolean;
  onClose: () => void;
}

export function FeedTestDialog({ feed, open, onClose }: Props) {
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState<{
    success: boolean;
    message?: string;
    preview?: Record<string, unknown>;
  } | null>(null);

  const handleTest = async () => {
    if (!feed) return;

    setTesting(true);
    setResult(null);

    try {
      const response = await api.testFeed(feed.guildId, feed.id);
      setResult({
        success: true,
        message: "Feed test successful! Preview below:",
        preview: response,
      });
    } catch (error) {
      setResult({
        success: false,
        message: error instanceof Error ? error.message : "Test failed. Please check your feed configuration.",
      });
    } finally {
      setTesting(false);
    }
  };

  const handleClose = () => {
    setResult(null);
    onClose();
  };

  if (!feed) return null;

  return (
    <Sheet open={open} onOpenChange={(o) => !o && handleClose()}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            Test Feed
            <Badge variant="outline">{feed.feedType}</Badge>
          </SheetTitle>
        </SheetHeader>

        <div className="mt-6 space-y-4">
          <div>
            <p className="text-sm text-muted-foreground">Target</p>
            <p className="font-medium">{feed.targetIdentifier}</p>
          </div>

          <Separator />

          <div>
            <p className="text-sm text-muted-foreground">Status</p>
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
          </div>

          <Separator />

          <div>
            <p className="text-sm text-muted-foreground">Last Check</p>
            <p className="font-medium">
              {feed.lastCheckTime
                ? new Date(feed.lastCheckTime).toLocaleString()
                : "Never checked"}
            </p>
          </div>

          <Separator />

          <div className="space-y-3">
            <Button
              onClick={handleTest}
              disabled={testing}
              className="w-full"
              variant="default"
            >
              {testing ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Testing...
                </>
              ) : (
                <>
                  <TestTube className="mr-2 h-4 w-4" />
                  Run Test
                </>
              )}
            </Button>

            {result && (
              <div
                className={`rounded-md border p-3 ${
                  result.success
                    ? "border-green-500/50 bg-green-500/10"
                    : "border-destructive bg-destructive/10"
                }`}
              >
                <div className="flex items-start gap-2">
                  {result.success ? (
                    <CheckCircle className="h-4 w-4 text-green-500 mt-0.5" />
                  ) : (
                    <XCircle className="h-4 w-4 text-destructive mt-0.5" />
                  )}
                  <div className="flex-1">
                    <p className={`text-sm ${result.success ? "text-green-500" : "text-destructive"}`}>
                      {result.message}
                    </p>
                  </div>
                </div>
              </div>
            )}

            {result?.success && result.preview && (
              <div className="space-y-2">
                <p className="text-sm font-medium">Notification Preview:</p>
                <div className="rounded-md border bg-muted/50 p-3">
                  <pre className="text-xs whitespace-pre-wrap break-all">
                    {JSON.stringify(result.preview, null, 2)}
                  </pre>
                </div>
              </div>
            )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
