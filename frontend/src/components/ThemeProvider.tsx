"use client";

import { createContext, useContext, useEffect, useState } from "react";
import type { Theme } from "@/lib/types";

const STORAGE_KEY = "hebrew-visualizer-theme";

const ThemeContext = createContext<{
  theme: Theme;
  toggleTheme: () => void;
} | null>(null);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  // Dark is the product default (see FRONTEND_PLAN.md's Color system).
  // Lazy-init reads localStorage on the client only (window is undefined
  // during SSR, so the server-rendered/hydration-time value is "dark",
  // matching layout.tsx's static data-theme="dark" - a stored "light"
  // preference applies right after via the effect below).
  const [theme, setTheme] = useState<Theme>(() => {
    if (typeof window === "undefined") return "dark";
    return localStorage.getItem(STORAGE_KEY) === "light" ? "light" : "dark";
  });

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(STORAGE_KEY, theme);
  }, [theme]);

  function toggleTheme() {
    setTheme((t) => (t === "dark" ? "light" : "dark"));
  }

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>{children}</ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within ThemeProvider");
  return ctx;
}
