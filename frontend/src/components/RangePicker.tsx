"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

/** Minimal navigation form for the dynamic /read/[book]?start=&end= route. */
export function RangePicker({ book }: { book: string }) {
  const router = useRouter();
  const [start, setStart] = useState("1.1");
  const [end, setEnd] = useState("3.5");

  function goToRange(e: FormEvent) {
    e.preventDefault();
    router.push(`/read/${book}?start=${start}&end=${end}`);
  }

  return (
    <form onSubmit={goToRange} className="flex flex-wrap items-end gap-3">
      <label className="flex flex-col text-sm">
        Start (chapter.verse)
        <input
          value={start}
          onChange={(e) => setStart(e.target.value)}
          className="rounded border px-2 py-1"
          style={{ borderColor: "var(--color-muted-border)" }}
          placeholder="1.1"
        />
      </label>
      <label className="flex flex-col text-sm">
        End (chapter.verse)
        <input
          value={end}
          onChange={(e) => setEnd(e.target.value)}
          className="rounded border px-2 py-1"
          style={{ borderColor: "var(--color-muted-border)" }}
          placeholder="3.5"
        />
      </label>
      <button
        type="submit"
        className="rounded border px-3 py-1"
        style={{ borderColor: "var(--color-muted-border)" }}
      >
        Read
      </button>
    </form>
  );
}
