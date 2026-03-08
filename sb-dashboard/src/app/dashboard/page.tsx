"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Navbar } from "@/components/layout/Navbar";
import { useUser } from "@/lib/user-context";
import Link from "next/link";

export default function DashboardPage() {
  const user = useUser();
  const guilds = user.guilds;

  return (
    <>
      <Navbar user={user} guilds={guilds} />
      <div className="flex-1 p-6">
        <h2 className="mb-6 text-2xl font-bold">Select a Server</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {guilds.map((guild) => {
            const iconUrl = guild.icon
              ? `https://cdn.discordapp.com/icons/${guild.id}/${guild.icon}.png?size=128`
              : undefined;

            return (
              <Link
                key={guild.id}
                href={guild.botPresent ? `/dashboard/${guild.id}` : "#"}
                className={!guild.botPresent ? "pointer-events-none opacity-50" : ""}
              >
                <Card className="transition-colors hover:bg-accent/50">
                  <CardHeader className="flex flex-row items-center gap-3 pb-2">
                    <Avatar className="h-10 w-10">
                      <AvatarImage src={iconUrl} alt={guild.name} />
                      <AvatarFallback>{guild.name?.[0]?.toUpperCase() ?? "?"}</AvatarFallback>
                    </Avatar>
                    <CardTitle className="text-base">{guild.name}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {guild.botPresent ? (
                      <Badge variant="secondary">Bot Active</Badge>
                    ) : (
                      <Badge variant="outline">Bot Not Added</Badge>
                    )}
                  </CardContent>
                </Card>
              </Link>
            );
          })}
        </div>
      </div>
    </>
  );
}
