# Sutra Cut — AI Video Auto-Editor

Full-stack editor for Hindi, English, and mixed-language videos.

Upload raw audio/video → transcribe → edit transcript → auto-select b-roll/icons → styled captions → preview segments → render → download.

## Architecture

```
React (Vite :5173)
  -> Spring Boot 4.1.1 (:8080)
    -> Python FastAPI worker (:8001)
      -> faster-whisper + FFmpeg + Pexels/Pixabay/Iconify
```

- Frontend: upload, transcript editor, timeline, preview
- Spring Boot: APIs, projects, EDL, async render jobs
- Python worker: transcription, asset matching, FFmpeg rendering

## Phase coverage

1. Upload → transcribe → edit transcript → styled captions → download
2. Keyword extraction, Pexels/Pixabay/Iconify matching, EDL
3. Segment preview, visual swap, dirty-segment rendering
4. Progress UI, caption styles, fallback caption-only slides

## Prerequisites

- JDK 17+
- Maven 3.8+
- Python 3.11+
- Node.js 18+
- FFmpeg and ffprobe

## Run locally

```bash
# Optional API keys for stock footage
cp .env.example .env

# Create worker venv and install deps
python3 -m venv worker/venv
worker/venv/bin/pip install -r worker/requirements.txt

# Frontend deps
cd frontend && npm install && cd ..

# Start worker, Spring Boot, and Vite
chmod +x start.sh
./start.sh
```

Open `http://localhost:5173`.

The Vite dev server reverse-proxies `/api` to Spring Boot on port 8080.

## Main APIs

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/upload` | Store media and queue transcription |
| GET/PUT | `/api/projects/{id}/transcript` | Read/edit transcript |
| GET/PUT | `/api/projects/{id}/edl` | Read/edit EDL |
| POST | `/api/projects/{id}/edl/generate` | Match visuals |
| POST | `/api/projects/{id}/render` | Queue final render |
| GET | `/api/projects/{id}/render/status` | Poll job status |
| GET | `/api/projects/{id}/download` | Download MP4 |
| GET/PUT | `/api/projects` / `/api/projects/{id}` | Project CRUD |

Worker:

- `POST /transcribe`
- `POST /match-assets`
- `POST /render-segment`
- `POST /render-final`

## Fallback rules

External calls retry once, then switch provider. If Pexels, Pixabay, and Iconify all miss, the segment renders as a caption-only slide. A missing asset never fails the whole video.

H2 file database is the default so the stack runs without PostgreSQL. Point `spring.datasource.url` at PostgreSQL when you want the production store.

## Storage

Uploads, clips, and finals live under `storage/{projectId}/`. Temporary files are deleted after each processing stage.
