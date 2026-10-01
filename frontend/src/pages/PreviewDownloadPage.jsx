import { useEffect, useState } from "react";
import VideoPlayerPreview from "../components/VideoPlayerPreview.jsx";
import { downloadUrl, getRenderStatus, startRender } from "../api/client.js";
import { useProject } from "../state/projectStore.js";

export default function PreviewDownloadPage() {
  const { project, setBusy, busy, setError } = useProject();
  const [status, setStatus] = useState(null);

  useEffect(() => {
    if (!project?.id) {
      return;
    }
    let timer = 0;
    async function poll() {
      try {
        const latest = await getRenderStatus(project.id);
        setStatus(latest);
        if (latest.status && !["COMPLETED", "FAILED"].includes(latest.status)) {
          timer = window.setTimeout(poll, 2000);
        }
      } catch (err) {
        setError(err.message);
      }
    }
    poll();
    return () => window.clearTimeout(timer);
  }, [project?.id]);

  async function render() {
    setBusy(true);
    try {
      const started = await startRender(project.id);
      setStatus(started);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (!project) {
    return <p className="meta">Upload a video first.</p>;
  }

  const ready = status?.downloadReady;

  return (
    <div className="layout-2">
      <section className="panel">
        <div className="kicker">Final cut</div>
        <h2 style={{ marginTop: 0, fontFamily: "var(--serif)" }}>Burn captions, concatenate clips, download.</h2>
        <p className="lede">{status?.message || "Queue an async FFmpeg job. The UI polls Spring Boot until the file is ready."}</p>
        <div className="progress" style={{ margin: "16px 0" }}>
          <span style={{ width: `${status?.progress || 0}%` }} />
        </div>
        <div className="row">
          <button type="button" className="primary" onClick={render} disabled={busy}>Generate final</button>
          {ready ? (
            <a className="ghost" href={downloadUrl(project.id)}>Download MP4</a>
          ) : null}
        </div>
      </section>
      <aside>
        <VideoPlayerPreview src={ready ? downloadUrl(project.id) : undefined} />
      </aside>
    </div>
  );
}
