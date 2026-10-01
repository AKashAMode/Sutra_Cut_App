import { NavLink, Route, Routes, useLocation } from "react-router-dom";
import UploadPage from "./pages/UploadPage.jsx";
import TranscriptEditorPage from "./pages/TranscriptEditorPage.jsx";
import TimelineEditorPage from "./pages/TimelineEditorPage.jsx";
import PreviewDownloadPage from "./pages/PreviewDownloadPage.jsx";
import { useProject } from "./state/projectStore.js";

const STEPS = [
  { to: "/", label: "Upload", end: true },
  { to: "/transcript", label: "Transcript" },
  { to: "/timeline", label: "Timeline" },
  { to: "/preview", label: "Render" },
];

export default function App() {
  const { project, error } = useProject();
  const location = useLocation();

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink to="/" className="brand">
          <div className="mark">स</div>
          <div>
            <h1>Sutra Cut</h1>
            <p>Hindi + English AI video editor</p>
          </div>
        </NavLink>
        <nav className="nav">
          {STEPS.map((step) => (
            <NavLink
              key={step.to}
              to={step.to}
              end={step.end}
              className={({ isActive }) => (isActive || (step.to !== "/" && location.pathname.startsWith(step.to)) ? "active" : "")}
            >
              {step.label}
            </NavLink>
          ))}
        </nav>
        {project ? <span className="status-pill">{project.name}</span> : <span className="meta">No project loaded</span>}
      </header>
      <main className="page">
        {error ? <div className="toast">{error}</div> : null}
        <Routes>
          <Route path="/" element={<UploadPage />} />
          <Route path="/transcript" element={<TranscriptEditorPage />} />
          <Route path="/timeline" element={<TimelineEditorPage />} />
          <Route path="/preview" element={<PreviewDownloadPage />} />
        </Routes>
      </main>
    </div>
  );
}
