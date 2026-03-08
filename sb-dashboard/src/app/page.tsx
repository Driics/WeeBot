"use client";

import { Button } from "@/components/ui/button";
import { loginWithDiscord } from "@/lib/auth";
import { Bot, Shield, BarChart3, Music } from "lucide-react";

export default function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background">
      <div className="mx-auto flex max-w-2xl flex-col items-center gap-8 px-4 text-center">
        <div className="flex items-center gap-3">
          <Bot className="h-12 w-12 text-primary" />
          <h1 className="text-4xl font-bold tracking-tight">SableBot</h1>
        </div>

        <p className="text-lg text-muted-foreground">
          The all-in-one Discord bot for moderation, music, and server management.
          Configure everything from your browser.
        </p>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="flex flex-col items-center gap-2 rounded-lg border p-4">
            <Shield className="h-8 w-8 text-chart-1" />
            <span className="text-sm font-medium">Moderation</span>
          </div>
          <div className="flex flex-col items-center gap-2 rounded-lg border p-4">
            <BarChart3 className="h-8 w-8 text-chart-2" />
            <span className="text-sm font-medium">Analytics</span>
          </div>
          <div className="flex flex-col items-center gap-2 rounded-lg border p-4">
            <Music className="h-8 w-8 text-chart-3" />
            <span className="text-sm font-medium">Music</span>
          </div>
        </div>

        <Button size="lg" onClick={loginWithDiscord} className="gap-2">
          <svg className="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
            <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03z" />
          </svg>
          Sign in with Discord
        </Button>
      </div>
    </div>
  );
}
