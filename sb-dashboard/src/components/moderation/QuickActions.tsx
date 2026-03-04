"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
} from "@/components/ui/dialog";
import { Gavel, UserX, AlertTriangle } from "lucide-react";
import { toast } from "sonner";
import { api } from "@/lib/api";

interface Props {
  guildId: string;
  onActionComplete: () => void;
}

function ActionDialog({
  title,
  icon: Icon,
  variant,
  showDuration,
  onSubmit,
}: {
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  variant: "default" | "destructive";
  showDuration?: boolean;
  onSubmit: (targetId: string, reason: string, duration?: string) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [targetId, setTargetId] = useState("");
  const [reason, setReason] = useState("");
  const [duration, setDuration] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit() {
    if (!targetId.trim()) {
      toast.error("Target user ID is required");
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit(targetId.trim(), reason.trim(), duration.trim() || undefined);
      setOpen(false);
      setTargetId("");
      setReason("");
      setDuration("");
    } catch {
      toast.error(`Failed to execute ${title.toLowerCase()}`);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant={variant} size="sm" className="gap-2">
          <Icon className="h-4 w-4" />
          {title}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label htmlFor="target">Target User ID</Label>
            <Input
              id="target"
              value={targetId}
              onChange={(e) => setTargetId(e.target.value)}
              placeholder="123456789012345678"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="reason">Reason</Label>
            <Input
              id="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Optional reason"
            />
          </div>
          {showDuration && (
            <div className="space-y-2">
              <Label htmlFor="duration">Duration</Label>
              <Input
                id="duration"
                value={duration}
                onChange={(e) => setDuration(e.target.value)}
                placeholder="e.g. 7d, 24h"
              />
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button variant={variant} onClick={handleSubmit} disabled={submitting}>
            {submitting ? "Executing..." : `Confirm ${title}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function QuickActions({ guildId, onActionComplete }: Props) {
  return (
    <div className="flex flex-wrap gap-2">
      <ActionDialog
        title="Ban"
        icon={Gavel}
        variant="destructive"
        showDuration
        onSubmit={async (targetId, reason, duration) => {
          await api.executeBan(guildId, { targetId, reason, duration });
          toast.success("Ban executed");
          onActionComplete();
        }}
      />
      <ActionDialog
        title="Kick"
        icon={UserX}
        variant="destructive"
        onSubmit={async (targetId, reason) => {
          await api.executeKick(guildId, { targetId, reason });
          toast.success("Kick executed");
          onActionComplete();
        }}
      />
      <ActionDialog
        title="Warn"
        icon={AlertTriangle}
        variant="default"
        onSubmit={async (targetId, reason) => {
          await api.executeWarn(guildId, { targetId, reason });
          toast.success("Warning issued");
          onActionComplete();
        }}
      />
    </div>
  );
}
