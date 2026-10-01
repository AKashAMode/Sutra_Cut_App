import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import SegmentEditModal from "../components/SegmentEditModal.jsx";
import TimelineTrack from "../components/TimelineTrack.jsx";
import VideoPlayerPreview from "../components/VideoPlayerPreview.jsx";
import { getEdl, getProject, saveEdl, sourceUrl } from "../api/client.js";
import { useProject } from "../state/projectStore.js";

export default function TimelineEditorPage() {
  const navigate = useNavigate();
  const { project, setProject, edl, setEdl, setBusy, busy, setError } = useProject();
  const [active, setActive] = useState(null);

  useEffect(() => {
    if (!project?.id) {
      return;
    }
    let timer = 0;
    async function load() {
      try {
        const latest = await getProject(project.id);
        setProject(latest);
        const data = await getEdl(project.id);
        setEdl(data.segments || []);
        if (latest.status === "MATCHING_ASSETS") {
          timer = window.setTimeout(load, 2000);
        }
      } catch (err) {
        setError(err.message);
      }
    }
    load();
    return () => window.clearTimeout(timer);
  }, [project?.id]);

  async function save(next) {
    const segments = edl.map((item) => (item.id === next.id ? { ...item, ...next, dirty: true } : item));
    setEdl(segments);
    setActive(null);
    try {
      const saved = await saveEdl(project.id, segments);
      setEdl(saved.segments || []);
    } catch (err) {
      setError(err.message);
    }
  }

  if (!project) {
    return <p className="meta">Upload a video first.</p>;
  }

  return (
    <>
      <section className="panel">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <div>
            <div className="kicker">Edit decision list</div>
            <h2 style={{ margin: "0 0 8px", fontFamily: "var(--serif)" }}>Swap a visual without re-rendering everything.</h2>
          </div>
          <button type="button" className="primary" disabled={busy || edl.length === 0} onClick={() => navigate("/preview")}>
            Generate final
          </button>
        </div>
        <TimelineTrack segments={edl} activeId={active?.id} onSelect={setActive} />
        {edl.length === 0 ? <p className="meta">{project.statusMessage || "Matching assets..."}</p> : null}
      </section>
      <div className="layout-2" style={{ marginTop: 18 }}>
        <VideoPlayerPreview src={sourceUrl(project.id)} />
        <div className="panel">
          <p className="lede">Dashed clips are dirty and will re-render. Caption-only is used if Pexels, Pixabay, and Iconify all miss.</p>
          <p className="footer-note">Source: {project.originalFilename}</p>
        </div>
      </div>
      {active ? <SegmentEditModal segment={active} onClose={() => setActive(null)} onSave={save} /> : null}
    </>
  );
}
