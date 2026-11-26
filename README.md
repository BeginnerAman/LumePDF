# LumePDF

Ultra-lightweight, fast Android PDF reader and organizer with an Apple-inspired Glassmorphism UI.

## Features
- **Auto-Scan Device PDFs**: Automatically detects PDFs across your phone storage (plus manual **Add PDF** picker).
- **Categories**: Organize PDFs into built-in or custom categories (*Study, Work, Bills, Books*, or create your own).
- **Date-Wise Grouping & Sorting**: Group by *Today / Yesterday / This Week / Month* and sort by Newest, Oldest, Name (A–Z / Z–A), or Size.
- **Fast Search & Clean Delete**: Instant filename search and safe in-app deletion with confirmation.
- **Zero-Bloat Native PDF Reader**: Powered by Android's built-in `PdfRenderer` with bounded LRU bitmap caching, pinch-to-zoom, double-tap zoom, page scrubber, night reading contrast mode, and last-read page memory.

## Cloud Build (GitHub Actions)
Every push to `main` builds `LumePDF-release.apk` and `LumePDF-debug.apk` via GitHub Actions and publishes them to **GitHub Releases**.
