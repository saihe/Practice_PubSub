"use client";

import { Transition } from "@headlessui/react";
import { useEffect, useState } from "react";

export type ToastTone = "success" | "warning" | "error" | "info";

export interface Toast {
  id: string;
  tone: ToastTone;
  message: string;
}

const TONE_STYLES: Record<ToastTone, { bar: string; icon: string }> = {
  success: { bar: "border-l-emerald-500", icon: "✓" },
  warning: { bar: "border-l-amber-500", icon: "⚠" },
  error: { bar: "border-l-red-500", icon: "✕" },
  info: { bar: "border-l-blue-500", icon: "ℹ" },
};

/** 1件のトースト。マウントで enter、5.5秒後に leave → afterLeave で親から除去。 */
function ToastItem({ toast, onDone }: { toast: Toast; onDone: (id: string) => void }) {
  const [show, setShow] = useState(false);
  const tone = TONE_STYLES[toast.tone];

  useEffect(() => {
    setShow(true);
    const timer = setTimeout(() => setShow(false), 5500);
    return () => clearTimeout(timer);
  }, []);

  return (
    <Transition
      show={show}
      appear
      enter="transform transition duration-300 ease-out"
      enterFrom="translate-x-6 opacity-0"
      enterTo="translate-x-0 opacity-100"
      leave="transform transition duration-200 ease-in"
      leaveFrom="translate-x-0 opacity-100"
      leaveTo="translate-x-6 opacity-0"
      afterLeave={() => onDone(toast.id)}
    >
      <div
        className={`flex w-80 items-start gap-3 rounded-lg border border-slate-200 border-l-4 bg-white p-4 shadow-lg ${tone.bar}`}
        role="status"
      >
        <span className="mt-0.5 text-lg leading-none">{tone.icon}</span>
        <p className="flex-1 text-sm text-slate-700">{toast.message}</p>
        <button
          type="button"
          onClick={() => setShow(false)}
          className="text-slate-400 hover:text-slate-600"
          aria-label="閉じる"
        >
          ✕
        </button>
      </div>
    </Transition>
  );
}

/** 画面右下に積み重ねるトースト群。 */
export function SnackbarStack({
  toasts,
  onDismiss,
}: {
  toasts: Toast[];
  onDismiss: (id: string) => void;
}) {
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((t) => (
        <div key={t.id} className="pointer-events-auto">
          <ToastItem toast={t} onDone={onDismiss} />
        </div>
      ))}
    </div>
  );
}
