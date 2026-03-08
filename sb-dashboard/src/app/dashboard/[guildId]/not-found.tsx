import Link from "next/link";

export default function GuildNotFound() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-12">
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-muted-foreground">Server not found</p>
      <Link
        href="/dashboard"
        className="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90"
      >
        Back to dashboard
      </Link>
    </div>
  );
}
