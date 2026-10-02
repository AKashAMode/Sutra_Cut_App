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

### Windows PowerShell

Install FFmpeg (including `ffprobe`) and prepare the worker environment:

```powershell
winget install --id Gyan.FFmpeg.Shared --exact
py -3.11 -m venv worker\venv
worker\venv\Scripts\python.exe -m pip install -r worker\requirements.txt
```

Restart PowerShell after installing FFmpeg so `ffmpeg` and `ffprobe` are on `PATH`.

Start the worker, backend, and frontend in separate PowerShell windows:

```powershell
# Worker
$env:STORAGE_ROOT = "$PWD\storage"
Set-Location worker
.\venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8001
```

```powershell
# Backend (run from the repository root)
$env:STORAGE_ROOT = "$PWD\storage"
$env:WORKER_BASE_URL = "http://127.0.0.1:8001"
Set-Location backend
mvn spring-boot:run
```

```powershell
# Frontend (run from the repository root)
Set-Location frontend
npm install
npm run dev
```

Check `http://localhost:8001/health` before uploading. It must report `"status":"ok"`,
`"ffmpeg":true`, and `"whisper":true`.

### macOS / Linux

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
