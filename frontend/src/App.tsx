import { useState } from "react";
import { fetchVerseRange } from "./api";
import type { VerseResponse } from "./types";
import { VerseRow } from "./components/VerseRow";
import "./App.css";

export default function App() {
  const [chapter, setChapter] = useState(1);
  const [startVerse, setStartVerse] = useState(1);
  const [endVerse, setEndVerse] = useState(20);
  const [verses, setVerses] = useState<VerseResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadRange(e?: React.FormEvent) {
    e?.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const data = await fetchVerseRange("Gen", chapter, startVerse, endVerse);
      setVerses(data);
    } catch (err) {
      setError(
        err instanceof Error
          ? `${err.message} — is the backend running on :8080?`
          : "Failed to load verses",
      );
      setVerses([]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app">
      <header>
        <h1>Hebrew Text Rarity Visualizer</h1>
        <p className="subtitle">Genesis only, for now</p>
      </header>

      <form className="controls" onSubmit={loadRange}>
        <label>
          Chapter
          <input
            type="number"
            min={1}
            value={chapter}
            onChange={(e) => setChapter(Number(e.target.value))}
          />
        </label>
        <label>
          Start verse
          <input
            type="number"
            min={1}
            value={startVerse}
            onChange={(e) => setStartVerse(Number(e.target.value))}
          />
        </label>
        <label>
          End verse
          <input
            type="number"
            min={1}
            value={endVerse}
            onChange={(e) => setEndVerse(Number(e.target.value))}
          />
        </label>
        <button type="submit" disabled={loading}>
          {loading ? "Loading…" : "Load"}
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      <main className="reading-pane">
        {verses.length === 0 && !loading && !error && (
          <p className="hint">Load a range to see it.</p>
        )}
        {verses.map((v) => (
          <VerseRow key={v.osisId} verse={v} />
        ))}
      </main>

      <p className="legend">
        <span style={{ color: "#FFFFFF" }}>white</span> = root seen once ·{" "}
        <span style={{ color: "#00C800" }}>green</span> = seen twice, a
        signal to compare passages ·{" "}
        <span style={{ color: "#A00000" }}>dark red</span> = common,
        structural word
      </p>
    </div>
  );
}
