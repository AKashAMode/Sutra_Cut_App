import { createContext, createElement, useContext, useMemo, useState } from "react";

const ProjectContext = createContext(null);

export function ProjectProvider({ children }) {
  const [project, setProject] = useState(null);
  const [transcript, setTranscript] = useState([]);
  const [edl, setEdl] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const value = useMemo(
    () => ({
      project,
      setProject,
      transcript,
      setTranscript,
      edl,
      setEdl,
      busy,
      setBusy,
      error,
      setError,
    }),
    [project, transcript, edl, busy, error]
  );

  return createElement(ProjectContext.Provider, { value }, children);
}

export function useProject() {
  const ctx = useContext(ProjectContext);
  if (!ctx) {
    throw new Error("useProject must be used within ProjectProvider");
  }
  return ctx;
}
