import type { Metadata } from "next";
import { Geist, Geist_Mono, Noto_Serif_Hebrew } from "next/font/google";
import Link from "next/link";
import { ThemeProvider } from "@/components/ThemeProvider";
import { ThemeToggle } from "@/components/ThemeToggle";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

// Hebrew typography is a stated priority (FRONTEND_PLAN.md) - needs to
// render consonants, vowel points, and cantillation marks clearly.
const notoSerifHebrew = Noto_Serif_Hebrew({
  variable: "--font-hebrew",
  subsets: ["hebrew"],
  weight: ["400", "500", "600"],
});

export const metadata: Metadata = {
  title: "Hebrew Text Rarity Visualizer",
  description: "See rare-root connections across the Hebrew Bible.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      data-theme="dark"
      className={`${geistSans.variable} ${geistMono.variable} ${notoSerifHebrew.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <ThemeProvider>
          <header
            className="flex items-center justify-between border-b px-6 py-3"
            style={{ borderColor: "var(--color-muted-border)" }}
          >
            <Link href="/" className="text-sm font-semibold">
              Hebrew Text Rarity Visualizer
            </Link>
            <ThemeToggle />
          </header>
          <main className="mx-auto w-full max-w-3xl flex-1 px-6 py-8">{children}</main>
        </ThemeProvider>
      </body>
    </html>
  );
}
