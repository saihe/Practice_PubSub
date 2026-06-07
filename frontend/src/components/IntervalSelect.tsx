"use client";

import {
  Listbox,
  ListboxButton,
  ListboxOption,
  ListboxOptions,
} from "@headlessui/react";

/** 選択可能な自動更新間隔(秒)。 */
export const INTERVAL_OPTIONS = [5, 10, 20, 30] as const;
export type IntervalSeconds = (typeof INTERVAL_OPTIONS)[number];

/**
 * 自動更新間隔セレクト(ヘッドレスUI Listbox)。
 * 5 / 10 / 20 / 30 秒から選択。既定は呼び出し側で 10 秒を渡す。
 */
export function IntervalSelect({
  value,
  onChange,
}: {
  value: IntervalSeconds;
  onChange: (v: IntervalSeconds) => void;
}) {
  return (
    <Listbox value={value} onChange={onChange}>
      <div className="relative">
        <label className="mb-1 block text-xs font-medium text-slate-500">
          切断時の再取得間隔
        </label>
        <ListboxButton className="relative w-32 cursor-pointer rounded-md border border-slate-300 bg-white py-2 pl-3 pr-9 text-left text-sm shadow-sm hover:border-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500">
          <span className="block truncate">{value} 秒ごと</span>
          <span className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2 text-slate-400">
            ▼
          </span>
        </ListboxButton>
        <ListboxOptions className="absolute z-20 mt-1 w-32 overflow-auto rounded-md border border-slate-200 bg-white py-1 text-sm shadow-lg focus:outline-none">
          {INTERVAL_OPTIONS.map((opt) => (
            <ListboxOption
              key={opt}
              value={opt}
              className="flex cursor-pointer items-center justify-between px-3 py-2 data-[focus]:bg-blue-50 data-[selected]:font-semibold"
            >
              {({ selected }) => (
                <>
                  <span>{opt} 秒ごと</span>
                  {selected && <span className="text-blue-600">✓</span>}
                </>
              )}
            </ListboxOption>
          ))}
        </ListboxOptions>
      </div>
    </Listbox>
  );
}
