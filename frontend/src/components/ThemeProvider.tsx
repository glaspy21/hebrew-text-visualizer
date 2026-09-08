"use client";

import { createContext, useCallback, useContext, useEffect, useSyncExternalStore } from "react";
import type { Theme } from "@/lib/types";

const STORAGE_KEY = "hebrew-visualizer-theme";

function subscribe(callback: () => void) {
  // "storage" only fires in OTHER tabs/windows for the same key, not this
  // one - toggleTheme below dispatches a synthetic event so this tab's own
  // change is picked up too.
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

function getSnapshot(): Theme {
  return localStorage.getItem(STORAGE_KEY) === "light" ? "light" : "dark";
}

// Dark is the product default (see FRONTEND_PLAN.md's Color system) - must
// match layout.tsx's static data-theme="dark" exactly, since this is what
// both SSR and the pre-hydration client render use. useSyncExternalStore
// (not a lazy useState initializer, which caused a real hydration mismatch
// here once localStorage actually held "light") is the React-endorsed way
// to read a browser-only store without server/client divergence: it uses
// this server snapshot during hydration, then switches to the live client
// value right after, without React treating it as a mismatch.
function getServerSnapshot(): Theme {
  return "dark";
}

const ThemeContext = createContext<{
  theme: Theme;
  toggleTheme: () => void;
} | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const theme = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
  }, [theme]);

  const toggleTheme = useCallback(() => {
    const next: Theme = theme === "dark" ? "light" : "dark";
    localStorage.setItem(STORAGE_KEY, next);
    window.dispatchEvent(new StorageEvent("storage", { key: STORAGE_KEY, newValue: next }));
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>{children}</ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within ThemeProvider");
  return ctx;
}
