"use client";

import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import type { CaseResponse } from "@/types";

interface Props {
  caseData: CaseResponse | null;
  open: boolean;
  onClose: () => void;
}

export function CaseDetail({ caseData, open, onClose }: Props) {
  if (!caseData) return null;

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            Case #{caseData.caseNumber}
            <Badge variant="outline">{caseData.type}</Badge>
          </SheetTitle>
        </SheetHeader>

        <div className="mt-6 space-y-4">
          <div>
            <p className="text-sm text-muted-foreground">Target</p>
            <p className="font-medium">{caseData.targetUsername}</p>
            <p className="text-xs text-muted-foreground">{caseData.targetId}</p>
          </div>

          <Separator />

          <div>
            <p className="text-sm text-muted-foreground">Moderator</p>
            <p className="font-medium">{caseData.moderatorUsername}</p>
            <p className="text-xs text-muted-foreground">{caseData.moderatorId}</p>
          </div>

          <Separator />

          <div>
            <p className="text-sm text-muted-foreground">Reason</p>
            <p className="font-medium">{caseData.reason ?? "No reason provided"}</p>
          </div>

          {caseData.duration && (
            <>
              <Separator />
              <div>
                <p className="text-sm text-muted-foreground">Duration</p>
                <p className="font-medium">{caseData.duration}</p>
              </div>
            </>
          )}

          <Separator />

          <div>
            <p className="text-sm text-muted-foreground">Date</p>
            <p className="font-medium">
              {new Date(caseData.createdAt).toLocaleString()}
            </p>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
