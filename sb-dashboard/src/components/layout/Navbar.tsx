"use client";

import { Bot, LogOut } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { GuildSelector } from "./GuildSelector";
import { api } from "@/lib/api";
import type { UserInfo, GuildInfo } from "@/types";
import Link from "next/link";

interface NavbarProps {
  user: UserInfo;
  guilds: GuildInfo[];
  currentGuildId?: string;
}

export function Navbar({ user, guilds, currentGuildId }: NavbarProps) {
  const avatarUrl = user.avatar
    ? `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.png`
    : undefined;

  return (
    <header className="sticky top-0 z-50 flex h-14 items-center justify-between border-b bg-background px-4">
      <div className="flex items-center gap-4">
        <Link href="/dashboard" className="flex items-center gap-2">
          <Bot className="h-6 w-6 text-primary" />
          <span className="text-lg font-semibold">SableBot</span>
        </Link>
        {currentGuildId && (
          <GuildSelector
            guilds={guilds}
            currentGuildId={currentGuildId}
          />
        )}
      </div>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-accent">
            <Avatar className="h-8 w-8">
              <AvatarImage src={avatarUrl} alt={user.username} />
              <AvatarFallback>{user.username?.[0]?.toUpperCase() ?? "?"}</AvatarFallback>
            </Avatar>
            <span className="hidden text-sm font-medium sm:inline">
              {user.username}
            </span>
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => api.logout().then(() => { window.location.href = "/"; })} className="cursor-pointer">
            <LogOut className="mr-2 h-4 w-4" />
            Sign out
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}
