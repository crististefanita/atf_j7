import { analyzeReview, canonicalCheckpoint, openMatch, openReview, playBot, playMatch, reviewAction, snapshot } from "./chess-engine.mjs";

let pending = Promise.resolve();
self.onmessage = ({ data }) => {
  pending = pending.then(() => respond(data ?? {}));
};

async function respond({ id, type, resources, archive, checkpoint, setup, expectedRevision, action, move, ply = -1 }) {
  try {
    if (type === "snapshot" && typeof resources === "string") {
      self.postMessage({ id, snapshot: snapshot(resources) });
    } else if (type === "review-open" && validArchive(archive)) {
      const canonical = canonicalCheckpoint(archive.checkpoint);
      const digest = await sha256(canonical);
      const view = JSON.parse(openReview(archive.id, canonical, archive.review, archive.bookmark ?? null, digest));
      self.postMessage({ id, view });
    } else if (type === "review-action" && validRevision(expectedRevision) && typeof action === "string" && Number.isInteger(ply) &&
        ply >= -1 && ply <= 2147483647) {
      self.postMessage({ id, ...JSON.parse(reviewAction(expectedRevision, action, ply)) });
    } else if (type === "match-open" && validMatchOpen(checkpoint, setup)) {
      const match = openMatch(checkpoint ?? null, setup?.mode ?? null, setup?.humanSide ?? null, setup?.level ?? null);
      self.postMessage({ id, match: JSON.parse(match) });
    } else if (type === "match-play" && validRevision(expectedRevision) && typeof move === "string") {
      self.postMessage({ id, ...JSON.parse(playMatch(expectedRevision, move)) });
    } else if (type === "match-bot" && validRevision(expectedRevision)) {
      self.postMessage({ id, ...JSON.parse(playBot(expectedRevision)) });
    } else if (type === "review-analyze" && typeof checkpoint === "string") {
      const canonical = canonicalCheckpoint(checkpoint);
      const digest = await sha256(canonical);
      const review = analyzeReview(canonical, digest, (progress, total) => self.postMessage({ id, progress, total }));
      self.postMessage({ id, archive: { id: crypto.randomUUID(), checkpoint: canonical, review } });
    } else {
      throw new TypeError("Invalid chess worker request");
    }
  } catch (failure) {
    self.postMessage({ id, error: String(failure) });
  }
}

function validRevision(value) {
  return Number.isInteger(value) && value >= 0 && value <= 2147483647;
}

function validMatchOpen(checkpoint, setup) {
  if (checkpoint != null) return typeof checkpoint === "string" && setup == null;
  return setup == null || setup.mode === "LOCAL" || (setup.mode === "AI" && ["WHITE", "BLACK"].includes(setup.humanSide)
    && ["BASIC", "ADVANCED", "HARD"].includes(setup.level));
}

async function sha256(checkpoint) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(checkpoint));
  return btoa(String.fromCharCode(...new Uint8Array(bytes)));
}

function validArchive(archive) {
  return archive && typeof archive.id === "string" && typeof archive.checkpoint === "string" &&
    typeof archive.review === "string" && (archive.bookmark == null || typeof archive.bookmark === "string");
}
