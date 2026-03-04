"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
} from "@/components/ui/dialog";
import { Plus, Copy, Check } from "lucide-react";
import { toast } from "sonner";
import { api } from "@/lib/api";

interface Props {
  guildId: string;
  onCreated: () => void;
}

const AVAILABLE_SCOPES = [
  { id: "read", label: "Read", description: "Read server data and stats" },
  { id: "config-write", label: "Config Write", description: "Modify server configuration" },
  { id: "mod-write", label: "Mod Write", description: "Execute moderation actions" },
];

export function CreateKeyDialog({ guildId, onCreated }: Props) {
  const [open, setOpen] = useState(false);
  const [scopes, setScopes] = useState<string[]>(["read"]);
  const [creating, setCreating] = useState(false);
  const [rawKey, setRawKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  function toggleScope(scope: string) {
    setScopes((s) =>
      s.includes(scope) ? s.filter((x) => x !== scope) : [...s, scope]
    );
  }

  async function handleCreate() {
    if (scopes.length === 0) {
      toast.error("Select at least one scope");
      return;
    }
    setCreating(true);
    try {
      const result = await api.createApiKey(guildId, { scopes });
      setRawKey(result.rawKey);
      onCreated();
    } catch {
      toast.error("Failed to create API key");
    } finally {
      setCreating(false);
    }
  }

  async function handleCopy() {
    if (!rawKey) return;
    try {
      await navigator.clipboard.writeText(rawKey);
      setCopied(true);
      toast.success("Copied to clipboard");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Failed to copy to clipboard");
    }
  }

  function handleClose() {
    setOpen(false);
    setRawKey(null);
    setScopes(["read"]);
    setCopied(false);
  }

  return (
    <Dialog open={open} onOpenChange={(o) => (o ? setOpen(true) : handleClose())}>
      <DialogTrigger asChild>
        <Button size="sm" className="gap-2">
          <Plus className="h-4 w-4" />
          Create API Key
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {rawKey ? "API Key Created" : "Create API Key"}
          </DialogTitle>
        </DialogHeader>

        {rawKey ? (
          <div className="space-y-4 py-4">
            <p className="text-sm text-muted-foreground">
              Copy this key now. You will not be able to see it again.
            </p>
            <div className="flex items-center gap-2">
              <code className="flex-1 rounded-md border bg-muted px-3 py-2 text-sm font-mono break-all">
                {rawKey}
              </code>
              <Button variant="outline" size="sm" onClick={handleCopy}>
                {copied ? (
                  <Check className="h-4 w-4" />
                ) : (
                  <Copy className="h-4 w-4" />
                )}
              </Button>
            </div>
          </div>
        ) : (
          <div className="space-y-4 py-4">
            <div className="space-y-3">
              <Label>Scopes</Label>
              {AVAILABLE_SCOPES.map((scope) => (
                <label
                  key={scope.id}
                  className="flex items-start gap-3 rounded-md border px-3 py-2 cursor-pointer hover:bg-accent"
                >
                  <input
                    type="checkbox"
                    checked={scopes.includes(scope.id)}
                    onChange={() => toggleScope(scope.id)}
                    className="mt-0.5 h-4 w-4 rounded border-input"
                  />
                  <div>
                    <p className="text-sm font-medium">{scope.label}</p>
                    <p className="text-xs text-muted-foreground">
                      {scope.description}
                    </p>
                  </div>
                </label>
              ))}
            </div>
          </div>
        )}

        <DialogFooter>
          {rawKey ? (
            <Button onClick={handleClose}>Done</Button>
          ) : (
            <>
              <Button variant="outline" onClick={handleClose}>
                Cancel
              </Button>
              <Button onClick={handleCreate} disabled={creating}>
                {creating ? "Creating..." : "Create Key"}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
