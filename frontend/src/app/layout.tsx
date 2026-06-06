import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Pub/Sub タスク管理",
  description: "Next.js + Spring Boot による Pub/Sub の動作確認用タスク管理画面",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ja">
      <body className="bg-slate-100 text-slate-800 antialiased">{children}</body>
    </html>
  );
}
