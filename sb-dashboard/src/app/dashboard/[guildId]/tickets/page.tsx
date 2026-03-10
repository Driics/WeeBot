"use client";

import { use, useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { TicketMetrics, TicketListResponse, TicketFilterParams } from "@/types";

export default function TicketsPage({
  params,
}: {
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);
  const [metrics, setMetrics] = useState<TicketMetrics | null>(null);
  const [tickets, setTickets] = useState<TicketListResponse | null>(null);
  const [filters, setFilters] = useState<TicketFilterParams>({
    page: 0,
    pageSize: 10,
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(() => {
    setLoading(true);
    setError(null);

    Promise.all([
      api.getTicketMetrics(guildId),
      api.getTickets(guildId, filters)
    ])
      .then(([metricsData, ticketsData]) => {
        setMetrics(metricsData);
        setTickets(ticketsData);
      })
      .catch((e) => setError(e.message || "Failed to load data"))
      .finally(() => setLoading(false));
  }, [guildId, filters]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-8">
        <p className="text-destructive">{error}</p>
        <button onClick={fetchData} className="mt-2 text-sm text-primary underline">Retry</button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Ticket System</h2>

      {/* Metrics Overview */}
      {metrics && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <MetricCard
            title="Total Tickets"
            value={metrics.totalTickets}
            description="All time tickets created"
          />
          <MetricCard
            title="Open Tickets"
            value={metrics.openTickets}
            description="Currently awaiting response"
            highlight={metrics.openTickets > 0}
          />
          <MetricCard
            title="Claimed Tickets"
            value={metrics.claimedTickets}
            description="Being worked on by staff"
          />
          <MetricCard
            title="Closed (7 days)"
            value={metrics.closedLast7d}
            description="Resolved in the past week"
          />
          <MetricCard
            title="Avg Response Time"
            value={
              metrics.avgResponseTimeMinutes !== null
                ? `${Math.round(metrics.avgResponseTimeMinutes)} min`
                : "N/A"
            }
            description="Time until first staff response"
          />
          <MetricCard
            title="Avg Resolution Time"
            value={
              metrics.avgResolutionTimeHours !== null
                ? `${metrics.avgResolutionTimeHours.toFixed(1)} hrs`
                : "N/A"
            }
            description="Time from open to close"
          />
        </div>
      )}

      {/* Recent Tickets */}
      <div className="rounded-lg border bg-card">
        <div className="border-b p-4">
          <h3 className="font-semibold">Recent Tickets</h3>
        </div>
        <div className="p-4">
          {tickets && tickets.tickets.length > 0 ? (
            <div className="space-y-3">
              {tickets.tickets.map((ticket) => (
                <div
                  key={ticket.id}
                  className="flex items-center justify-between rounded-md border p-3 hover:bg-muted/50"
                >
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm text-muted-foreground">
                        #{ticket.ticketNumber}
                      </span>
                      <StatusBadge status={ticket.status} />
                    </div>
                    <p className="mt-1 font-medium">{ticket.subject}</p>
                    <p className="text-sm text-muted-foreground">
                      Created by {ticket.userName}
                      {ticket.assignedStaffName && (
                        <> · Assigned to {ticket.assignedStaffName}</>
                      )}
                    </p>
                  </div>
                  <div className="text-right text-sm text-muted-foreground">
                    {new Date(ticket.createdAt).toLocaleDateString()}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-center text-muted-foreground py-8">
              No tickets found
            </p>
          )}
        </div>
        {tickets && tickets.totalPages > 1 && (
          <div className="border-t p-4 flex items-center justify-between">
            <button
              onClick={() => setFilters((f) => ({ ...f, page: Math.max(0, (f.page || 0) - 1) }))}
              disabled={filters.page === 0}
              className="px-3 py-1 text-sm border rounded disabled:opacity-50"
            >
              Previous
            </button>
            <span className="text-sm text-muted-foreground">
              Page {(filters.page || 0) + 1} of {tickets.totalPages}
            </span>
            <button
              onClick={() => setFilters((f) => ({ ...f, page: (f.page || 0) + 1 }))}
              disabled={filters.page! >= tickets.totalPages - 1}
              className="px-3 py-1 text-sm border rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function MetricCard({
  title,
  value,
  description,
  highlight,
}: {
  title: string;
  value: string | number;
  description: string;
  highlight?: boolean;
}) {
  return (
    <div className={`rounded-lg border bg-card p-4 ${highlight ? "border-primary" : ""}`}>
      <h3 className="text-sm font-medium text-muted-foreground">{title}</h3>
      <p className="mt-2 text-3xl font-bold">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{description}</p>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const colors = {
    OPEN: "bg-blue-500/10 text-blue-500",
    CLAIMED: "bg-yellow-500/10 text-yellow-500",
    CLOSED: "bg-green-500/10 text-green-500",
    REOPENED: "bg-orange-500/10 text-orange-500",
  };

  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
        colors[status as keyof typeof colors] || "bg-gray-500/10 text-gray-500"
      }`}
    >
      {status}
    </span>
  );
}
