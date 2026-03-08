"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import {
  BarChart3,
  Settings,
  Shield,
  KeyRound,
  LayoutDashboard,
} from "lucide-react";

interface SidebarProps {
  guildId: string;
}

const navItems = [
  { label: "Overview", href: "", icon: LayoutDashboard },
  { label: "Stats", href: "/stats", icon: BarChart3 },
  { label: "Config", href: "/config", icon: Settings },
  { label: "Moderation", href: "/moderation", icon: Shield },
  { label: "API Keys", href: "/api-keys", icon: KeyRound },
];

export function Sidebar({ guildId }: SidebarProps) {
  const pathname = usePathname();
  const basePath = `/dashboard/${guildId}`;

  return (
    <aside className="hidden w-56 shrink-0 border-r bg-background md:block">
      <nav className="flex flex-col gap-1 p-4">
        {navItems.map((item) => {
          const href = `${basePath}${item.href}`;
          const isActive =
            item.href === ""
              ? pathname === basePath
              : pathname.startsWith(href);

          return (
            <Link
              key={item.label}
              href={href}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-accent text-accent-foreground"
                  : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              )}
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}

export function MobileSidebar({ guildId }: SidebarProps) {
  const pathname = usePathname();
  const basePath = `/dashboard/${guildId}`;

  return (
    <div className="flex gap-1 overflow-x-auto border-b p-2 md:hidden">
      {navItems.map((item) => {
        const href = `${basePath}${item.href}`;
        const isActive =
          item.href === ""
            ? pathname === basePath
            : pathname.startsWith(href);

        return (
          <Link
            key={item.label}
            href={href}
            className={cn(
              "flex shrink-0 items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
              isActive
                ? "bg-accent text-accent-foreground"
                : "text-muted-foreground hover:bg-accent"
            )}
          >
            <item.icon className="h-4 w-4" />
            {item.label}
          </Link>
        );
      })}
    </div>
  );
}
