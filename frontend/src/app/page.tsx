import Link from "next/link";
import { RangePicker } from "@/components/RangePicker";

export default function Home() {
  return (
    <div className="flex flex-col gap-8">
      <p className="opacity-70">
        A continuous Hebrew reader that colors words by how often their true
        root recurs in the range you&apos;re reading.
      </p>

      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wide opacity-60">
          Read a chapter
        </h2>
        <Link href="/read/Gen/1" className="underline">
          Genesis 1
        </Link>
      </section>

      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-semibold uppercase tracking-wide opacity-60">
          Read a custom range
        </h2>
        <RangePicker book="Gen" />
      </section>
    </div>
  );
}
