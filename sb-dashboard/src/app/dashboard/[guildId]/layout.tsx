"use client";

import { use } from "react";
import { redirect } from "next/navigation";
import { Sidebar, MobileSidebar } from "@/components/layout/Sidebar";
import { Navbar } from "@/components/layout/Navbar";
import { useUser } from "@/lib/user-context";
import { isValidSnowflake } from "@/lib/validation";

export default function GuildLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ guildId: string }>;
}) {
  const { guildId } = use(params);

  if (!isValidSnowflake(guildId)) {
    redirect("/dashboard");
  }

  const user = useUser();

  return (
    <>
      <Navbar user={user} guilds={user.guilds} currentGuildId={guildId} />
      <div className="flex flex-1">
        <Sidebar guildId={guildId} />
        <div className="flex flex-1 flex-col">
          <MobileSidebar guildId={guildId} />
          <main className="flex-1 p-6">{children}</main>
        </div>
      </div>
    </>
  );
}
