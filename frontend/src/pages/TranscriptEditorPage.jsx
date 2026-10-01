import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import TranscriptSegmentRow from "../components/TranscriptSegmentRow.jsx";
import VideoPlayerPreview from "../components/VideoPlayerPreview.jsx";
import { generateEdl, getProject, getTranscript, saveTranscript, sourceUrl } from "../api/client.js";
import { useProject } from "../state/projectStore.js";

export default function TranscriptEditorPage() {
  const navigate = useNavigate();
  const { project, setProject, transcript, setTranscript, setEdl, setBusy, busy, setError } = useProject();

  useEffect(() => {
    if (!project?.id) {
      return;
    }
    let timer = 0;
    async function poll() {
      try {
        const latest = await getProject(project.id);
        setProject(latest);
        const data = await getTranscript(project.id);
        setTranscript(data.segments || []);
        if (latest.status === "TRANSCRIBING" || latest.status === "QUEUED" || latest.status === "VALIDATING") {
          timer = window.setTimeout(poll, 2000);
        }
      } catch (err) {
        setError(err.message);
      }
    }
    poll();
    return () => window.clearTimeout(timer);
  }, [project?.id]);

  async function persist() {
    if (!project?.id) {
      return;
    }
    setBusy(true);
    try {
      const saved = await saveTranscript(project.id, transcript);
      setTranscript(saved.segments || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function buildEdl() {
    if (!project?.id) {
      return;
    }
    await persist();
    setBusy(true);
    try {
      const edl = await generateEdl(project.id);
      setEdl(edl.segments || []);
      navigate("/timeline");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (!project) {
    return <p className="meta">Upload a video first.</p>;
  }

  return (
    <div className="layout-2">
      <section className="panel">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <div>
            <div className="kicker">Transcript</div>
            <h2 style={{ margin: "0 0 8px", fontFamily: "var(--serif)" }}>Correct the words. Captions follow.</h2>
          </div>
          <span className={`status-pill ${project.status === "FAILED" ? "fail" : ""}`}>
            {project.status} · {project.progress || 0}%
          </span>
        </div>
        {transcript.map((segment, index) => (
          <TranscriptSegmentRow
            key={segment.id}
            segment={segment}
            onChange={(next) => {
              const copy = [...transcript];
              copy[index] = next;
              setTranscript(copy);
            }}
          />
        ))}
        {transcript.length === 0 ? <p className="meta">{project.statusMessage || "Waiting for transcription..."}</p> : null}
        <div className="row" style={{ marginTop: 12 }}>
          <button type="button" className="ghost" onClick={persist} disabled={busy}>Save transcript</button>
          <button type="button" className="primary" onClick={buildEdl} disabled={busy || transcript.length === 0}>
            Match visuals
          </button>
        </div>
      </section>
      <aside className="panel">
        <VideoPlayerPreview src={sourceUrl(project.id)} />
        <p className="footer-note">Source media streams from Spring Boot while Python transcribes in the background.</p>
      </aside>
    </div>
  );
}
