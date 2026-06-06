# デザイン仕様書（フロントエンド）

フロントエンドの見た目と振る舞いを **コンポーネント単位** で定義する。
記載内容：①役割、②配置（どこに置くか）、③構成プロパティ、④状態パターンと状態による変化。

- 技術: Next.js(App Router) / React / TypeScript / **Tailwind CSS** / **Headless UI**（UI ライブラリは不使用）。
- 配色トークン（Tailwind）:
  | 用途 | 色 |
  |---|---|
  | 背景 | `slate-100` |
  | 文字 | `slate-800` / 補助 `slate-500` |
  | プライマリ操作 | `blue-600`（hover `blue-700`） |
  | 注意/中止操作 | `amber-500`（hover `amber-600`） |
  | 成功 | `emerald-500` |
  | 警告 | `amber-500` |
  | 異常 | `red-500` |
  | 中止/無効 | `zinc`/`slate` |

## 配置マップ（画面ルート `app/page.tsx`）

```
<main max-w-6xl mx-auto px-4 py-8>
 ├─ Header               … タイトル＋説明（固定・最上部）
 ├─ ErrorBanner?         … エラー時のみ（Header の直下）
 ├─ <div space-y-4>
 │   ├─ Toolbar          … 一覧の上
 │   └─ TaskTable        … Toolbar の下
 ├─ Footer               … 一覧の下（操作注記）
 └─ SnackbarStack        … fixed 右下（フローティング、z-50）
```

`page.tsx` が保持する状態（State）:
| state | 役割 | 既定 |
|---|---|---|
| `tasks` | 一覧データ | `[]` |
| `selectedId` | 選択中タスクID | `null` |
| `intervalSeconds` | 自動更新間隔 | `10` |
| `creating`/`refreshing`/`cancelling` | 各操作の進行中フラグ | `false` |
| `lastUpdated` | 最終更新時刻 | `null` |
| `error` | エラーメッセージ | `null` |
| `toasts` | 表示中トースト配列 | `[]` |

---

## コンポーネント・カタログ

### 1. Header（`app/page.tsx` 内）
- **役割**: 画面タイトルと一言説明。
- **配置**: 最上部。
- **状態**: 静的（状態変化なし）。
- **デザイン**: `h1` は `text-2xl font-bold`、説明は `text-sm text-slate-500`。

### 2. ErrorBanner（`app/page.tsx` 内）
- **役割**: 通信エラーの通知。
- **配置**: Header 直下。`error` が非 null のときのみ描画。
- **状態**:
  | 状態 | 表示 |
  |---|---|
  | error=null | 非表示 |
  | error=有 | 赤系バナー（`border-red-200 bg-red-50 text-red-700`）＋「バックエンドが起動しているか確認」案内 |

### 3. Toolbar（`components/Toolbar.tsx`）
- **役割**: 3 つの操作ボタンと自動更新コントロールをまとめる。
- **配置**: テーブルの上。`flex justify-between`（左=ボタン群／右=更新状況＋間隔セレクト）。カード風（`rounded-xl border bg-white shadow-sm p-4`）。
- **プロパティ**: `onCreate/onRefresh/onCancel`, `canCancel`, `creating/refreshing/cancelling`, `intervalSeconds/onIntervalChange`, `lastUpdated`。
- **内包**: Button×3、IntervalSelect、最終更新表示。

#### 3-1. Button（バリアント）
Toolbar 内の 3 ボタンは色とラベルで区別する共通ボタン。

| バリアント | ラベル | 通常 | hover | 無効(disabled) | ローディング表示 |
|---|---|---|---|---|---|
| Primary | タスク実行（追加） | `bg-blue-600 text-white` | `bg-blue-700` | `opacity-50` | 「追加中…」 |
| Secondary | 画面更新 | `border border-slate-300 bg-white text-slate-700` | `bg-slate-50` | `opacity-50` | 「更新中…」 |
| Danger | タスク中止 | `bg-amber-500 text-white` | `bg-amber-600` | `bg-slate-300`（グレーアウト） | 「中止中…」 |

- **状態変化**:
  - Primary/Secondary: `creating`/`refreshing` が true の間ラベルが「…」表示かつ非活性。
  - Danger: `!canCancel || cancelling` で非活性（背景 `slate-300`）。`title` で理由を補足。

#### 3-2. IntervalSelect（`components/IntervalSelect.tsx`）— Headless UI `Listbox`
- **役割**: 自動更新間隔の選択（5/10/20/30 秒）。
- **配置**: Toolbar 右端。上にラベル「自動更新間隔」。
- **プロパティ**: `value: 5|10|20|30`, `onChange`。
- **状態パターン**:
  | 状態 | 見た目 |
  |---|---|
  | 閉 | ボタンに「{value} 秒ごと」＋ ▼ |
  | 開 | 下にオプションリスト（`absolute z-20 shadow-lg`） |
  | オプション focus | `bg-blue-50`（`data-[focus]`） |
  | オプション selected | 太字＋右に ✓（`blue-600`） |

#### 3-3. 最終更新表示
- 「自動更新: N 秒ごと」「最終更新: HH:MM:SS」。取得中は最終更新の右に `●`(青)。

### 4. TaskTable（`components/TaskTable.tsx`）
- **役割**: タスク一覧表。
- **配置**: Toolbar の下。`overflow-x-auto`（狭幅では横スクロール）。
- **プロパティ**: `tasks`, `selectedId`, `onSelect`。
- **状態パターン**:
  | 状態 | 表示 |
  |---|---|
  | 空（tasks=[]） | 1 行で「タスクはまだありません。…」（`text-slate-400` 中央寄せ） |
  | データあり | ヘッダ＋ TaskRow を反復 |

- **ヘッダ**: `bg-slate-50 text-xs uppercase text-slate-500`。列順は[画面仕様](spec-screen.md)の通り。

#### 4-1. TaskRow（TaskTable 内の `<tr>`）
- **役割**: 1 タスクの表示。
- **状態パターン**:
  | 状態 | 見た目 |
  |---|---|
  | 通常 | `hover:bg-slate-50`、行クリックで選択 |
  | 選択中（selected） | `bg-blue-50`、ラジオ ON |
  | 終端 | タスクID の右に淡色ドット `●`（終端マーク） |
- **セル詳細**:
  - 選択: `input[type=radio]`（`accent-blue-600`）。
  - タスクID: `font-mono text-xs`、先頭 8 桁、`title` に全 UUID。
  - 実行時間: `tabular-nums`。
  - 処理件数: 数値＋ ProgressBar ＋「成功x/失敗y」（`text-[10px] text-slate-400`）。

#### 4-2. ProgressBar（TaskRow 内）
- **役割**: `processedCount/totalCount` の進捗を可視化。
- **デザイン**: 幅 `w-20 h-1.5` のトラック（`bg-slate-100`）に塗り。
- **状態（色は status 連動）**:
  | status | 塗り色 |
  |---|---|
  | QUEUED/STARTED/SUCCESS | `blue-500` |
  | WARNING | `amber-400` |
  | ERROR | `red-400` |
  | CANCELLED | `zinc-400` |
  - 幅 = `Math.round(processed/total*100)%`。

### 5. StatusBadge（`components/StatusBadge.tsx`）
- **役割**: ステータスを色付きチップで表示。
- **配置**: TaskRow の「ステータス」セル。
- **プロパティ**: `status`, `label`。
- **状態パターン（6 種）**:
  | status | ラベル | チップ | ドット |
  |---|---|---|---|
  | QUEUED | キューに追加 | `slate-100/slate-700` | `slate-400` |
  | STARTED | 開始 | `blue-50/blue-700` | `blue-500`（`animate-pulse`） |
  | SUCCESS | 正常終了 | `emerald-50/emerald-700` | `emerald-500` |
  | WARNING | 警告終了 | `amber-50/amber-800` | `amber-500` |
  | ERROR | 異常終了 | `red-50/red-700` | `red-500` |
  | CANCELLED | 中止終了 | `zinc-100/zinc-700` | `zinc-500` |
  - 共通: `rounded-full px-2.5 py-0.5 text-xs ring-1 ring-inset`、左に状態ドット。
  - STARTED のみドットが脈動（処理中であることの視覚的フィードバック）。

### 6. SnackbarStack / ToastItem（`components/Snackbar.tsx`）— Headless UI `Transition`
- **役割**: 終了通知・操作通知のトースト表示。
- **配置**: `fixed bottom-4 right-4 z-50`、縦積み（`flex flex-col gap-2`）。
- **プロパティ**: `toasts: {id,tone,message}[]`, `onDismiss`。
- **トーン別デザイン（左ボーダー色＋アイコン）**:
  | tone | 左ボーダー | アイコン | 用途 |
  |---|---|---|---|
  | success | `emerald-500` | ✓ | 正常終了 |
  | warning | `amber-500` | ⚠ | 警告終了 |
  | error | `red-500` | ✕ | 異常終了 |
  | info | `blue-500` | ℹ | 中止終了・投入・中止要求 |
- **状態遷移（ToastItem）**:
  | フェーズ | 振る舞い |
  |---|---|
  | 出現 | マウント時に `Transition` で右からスライドイン（`translate-x-6→0`, `opacity 0→1`, 300ms） |
  | 表示 | 約 5.5 秒保持 |
  | 消滅 | スライドアウト（200ms）→ `afterLeave` で親配列から除去 |
  | 手動閉じ | × クリックで即 leave 開始 |
- カード: `w-80 rounded-lg border border-l-4 bg-white shadow-lg p-4`。

### 7. Footer（`app/page.tsx` 内）
- **役割**: 中止操作の説明注記。
- **配置**: テーブル下。`text-xs text-slate-400`。状態変化なし。

---

## 状態 × 見た目 早見表（タスク 1 行）

| status | バッジ | 進捗バー色 | 実行時間 | 中止ボタン（その行選択時） |
|---|---|---|---|---|
| QUEUED | 灰 | 青(0%) | — | 活性 |
| STARTED | 青(脈動) | 青(増加) | 増加中 | 活性 |
| SUCCESS | 緑 | 青(100%) | 確定 | 非活性 |
| WARNING | 琥珀 | 琥珀(100%) | 確定 | 非活性 |
| ERROR | 赤 | 赤(途中) | 確定 | 非活性 |
| CANCELLED | 亜鉛 | 亜鉛(途中) | 確定 | 非活性 |

## アクセシビリティ / その他
- ラジオに `aria-label`、トーストに `role=status`、ボタンに `title`。
- 数値列は `tabular-nums` で桁揃え。
- 狭幅では (D) テーブルが横スクロール（`overflow-x-auto`）。
