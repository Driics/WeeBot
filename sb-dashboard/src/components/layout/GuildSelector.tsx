"use client";

import { useRouter } from "next/navigation";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { GuildInfo } from "@/types";

interface GuildSelectorProps {
  guilds: GuildInfo[];
  currentGuildId: string;
}

export function GuildSelector({ guilds, currentGuildId }: GuildSelectorProps) {
  const router = useRouter();
  const manageable = guilds.filter((g) => g.botPresent);

  return (
    <Select
      value={currentGuildId}
      onValueChange={(id) => router.push(`/dashboard/${id}`)}
    >
      <SelectTrigger className="w-[200px]">
        <SelectValue placeholder="Select server" />
      </SelectTrigger>
      <SelectContent>
        {manageable.map((guild) => {
          const iconUrl = guild.icon
            ? `https://cdn.discordapp.com/icons/${guild.id}/${guild.icon}.png?size=32`
            : undefined;
          return (
            <SelectItem key={guild.id} value={guild.id}>
              <div className="flex items-center gap-2">
                <Avatar className="h-5 w-5">
                  <AvatarImage src={iconUrl} alt={guild.name} />
                  <AvatarFallback className="text-[10px]">
                    {guild.name?.[0]?.toUpperCase() ?? "?"}
                  </AvatarFallback>
                </Avatar>
                <span className="truncate">{guild.name}</span>
              </div>
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
}
