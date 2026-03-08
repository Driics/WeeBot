import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

function isValidJwtFormat(token: string): boolean {
  const parts = token.split(".");
  return parts.length === 3 && parts.every((p) => p.length > 0);
}

export function middleware(request: NextRequest) {
  const token = request.cookies.get("sb_token");

  if (!token || !isValidJwtFormat(token.value)) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: "/dashboard/:path*",
};
