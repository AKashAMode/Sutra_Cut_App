import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import UploadDropzone from "../components/UploadDropzone.jsx";
import { getProject, listProjects, uploadMedia } from "../api/client.js";
import { useProject } from "../state/projectStore.js";

export default function UploadPage() {
  const navigate = useNavigate();
  const { setProject, setBusy, busy, setError } = useProject();
  const [projects, setProjects] = useState([]);

  async function refresh() {
    try {
      setProjects(await listProjects());
    } catch (err) {
      setError(err.message);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function handleFile(file) {
    setBusy(true);
    setError("");
    try {
      const created = await uploadMedia(file, file.name.replace(/\.[^.]+$/, ""));
      setProject(created);
      navigate("/transcript");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
      refresh();
    }
  }

  async function openProject(id) {
    try {
      setProject(await getProject(id));
      navigate("/transcript");
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <>
      <section className="hero">
        <div>
          <div className="kicker">Phase 1-4 runnable</div>
          <h2>Cut mixed-language videos with a transcript, not a timeline of guesswork.</h2>
          <p className="lede">
            Upload raw Hindi, English, or mixed media. Whisper transcribes it, you edit the words,
            then Sutra Cut builds captions, b-roll, and a downloadable cut.
          </p>
        </div>
        <div className="panel">
          <UploadDropzone onFile={handleFile} disabled={busy} />
          {busy ? <p className="meta" style={{ marginTop: 12 }}>Uploading and starting transcription...</p> : null}
        </div>
      </section>
      <section>
        <h3>Recent projects</h3>
        <div className="grid">
          {projects.map((item) => (
            <button key={item.id} className="card" type="button" onClick={() => openProject(item.id)}>
              <h3>{item.name}</h3>
              <div className="meta">{item.status} · {item.originalFilename}</div>
              <div className="progress"><span style={{ width: `${item.progress || 0}%` }} /></div>
            </button>
          ))}
          {projects.length === 0 ? <div className="meta">No projects yet. Drop a clip to begin.</div> : null}
        </div>
      </section>
    </>
  );
}
