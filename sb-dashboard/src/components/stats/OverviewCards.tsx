"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Users, UserCheck, Terminal, Shield } from "lucide-react";
import type { StatsOverview } from "@/types";

interface Props {
  stats: StatsOverview;
}

export function OverviewCards({ stats }: Props) {
  const cards = [
    {
      title: "Total Members",
      value: stats.memberCount.toLocaleString(),
      icon: Users,
      color: "text-chart-1",
    },
    {
      title: "Active Today",
      value: stats.activeToday.toLocaleString(),
      icon: UserCheck,
      color: "text-chart-2",
    },
    {
      title: "Commands (24h)",
      value: stats.commandsLast24h.toLocaleString(),
      icon: Terminal,
      color: "text-chart-3",
    },
    {
      title: "Mod Actions (7d)",
      value: stats.modActionsLast7d.toLocaleString(),
      icon: Shield,
      color: "text-chart-4",
    },
  ];

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <Card key={card.title}>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {card.title}
            </CardTitle>
            <card.icon className={`h-4 w-4 ${card.color}`} />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{card.value}</div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
