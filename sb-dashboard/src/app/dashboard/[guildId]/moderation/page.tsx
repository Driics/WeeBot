"use client";

import { use, useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { CaseFilterParams, CaseListResponse, CaseResponse } from "@/types";
import { CaseFilters } from "@/components/moderation/CaseFilters";
import { CaseTable } from "@/components/moderation/CaseTable";
import { CaseDetail } from "@/components/moderation/CaseDetail";
import { QuickActions } from "@/components/moderation/QuickActions";

export default function ModerationPage({
  params,
}: {
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);
  const [filters, setFilters] = useState<CaseFilterParams>({
    page: 0,
    pageSize: 20,
  });
  const [data, setData] = useState<CaseListResponse | null>(null);
  const [selectedCase, setSelectedCase] = useState<CaseResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchCases = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .getCases(guildId, filters)
      .then(setData)
      .catch((e) => setError(e.message || "Failed to load cases"))
      .finally(() => setLoading(false));
  }, [guildId, filters]);

  useEffect(() => {
    fetchCases();
  }, [fetchCases]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="text-2xl font-bold">Moderation Console</h2>
        <QuickActions guildId={guildId} onActionComplete={fetchCases} />
      </div>

      <CaseFilters filters={filters} onChange={setFilters} />

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : error ? (
        <div className="text-center py-8">
          <p className="text-destructive">{error}</p>
          <button onClick={fetchCases} className="mt-2 text-sm text-primary underline">Retry</button>
        </div>
      ) : data ? (
        <CaseTable
          data={data}
          onPageChange={(page) => setFilters((f) => ({ ...f, page }))}
          onSelectCase={setSelectedCase}
        />
      ) : (
        <p className="text-center text-muted-foreground">Failed to load cases</p>
      )}

      <CaseDetail
        caseData={selectedCase}
        open={!!selectedCase}
        onClose={() => setSelectedCase(null)}
      />
    </div>
  );
}
