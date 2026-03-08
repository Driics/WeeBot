"use client";

import { use, useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { ApiKeyResponse } from "@/types";
import { ApiKeyTable } from "@/components/api-keys/ApiKeyTable";
import { CreateKeyDialog } from "@/components/api-keys/CreateKeyDialog";

export default function ApiKeysPage({
  params,
}: {
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);
  const [keys, setKeys] = useState<ApiKeyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchKeys = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .getApiKeys(guildId)
      .then(setKeys)
      .catch((e) => setError(e.message || "Failed to load API keys"))
      .finally(() => setLoading(false));
  }, [guildId]);

  useEffect(() => {
    fetchKeys();
  }, [fetchKeys]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold">API Keys</h2>
        <CreateKeyDialog guildId={guildId} onCreated={fetchKeys} />
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : error ? (
        <div className="text-center py-8">
          <p className="text-destructive">{error}</p>
          <button onClick={fetchKeys} className="mt-2 text-sm text-primary underline">Retry</button>
        </div>
      ) : (
        <ApiKeyTable guildId={guildId} keys={keys} onRevoked={fetchKeys} />
      )}
    </div>
  );
}
