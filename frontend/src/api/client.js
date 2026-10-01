const API = "/api";

async function parse(response) {
  const text = await response.text();
  const data = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new Error(data.message || data.detail || `Request failed (${response.status})`);
  }
  return data;
}

export async function listProjects() {
  return parse(await fetch(`${API}/projects`));
}

export async function getProject(id) {
  return parse(await fetch(`${API}/projects/${id}`));
}

export async function uploadMedia(file, name) {
  const body = new FormData();
  body.append("file", file);
  if (name) {
    body.append("name", name);
  }
  return parse(await fetch(`${API}/upload`, { method: "POST", body }));
}

export async function getTranscript(id) {
  return parse(await fetch(`${API}/projects/${id}/transcript`));
}

export async function saveTranscript(id, segments) {
  return parse(
    await fetch(`${API}/projects/${id}/transcript`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ segments }),
    })
  );
}

export async function getEdl(id) {
  return parse(await fetch(`${API}/projects/${id}/edl`));
}

export async function generateEdl(id) {
  return parse(await fetch(`${API}/projects/${id}/edl/generate`, { method: "POST" }));
}

export async function saveEdl(id, segments) {
  return parse(
    await fetch(`${API}/projects/${id}/edl`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ segments }),
    })
  );
}

export async function startRender(id) {
  return parse(await fetch(`${API}/projects/${id}/render`, { method: "POST" }));
}

export async function getRenderStatus(id) {
  return parse(await fetch(`${API}/projects/${id}/render/status`));
}

export function sourceUrl(id) {
  return `${API}/projects/${id}/source`;
}

export function downloadUrl(id) {
  return `${API}/projects/${id}/download`;
}
