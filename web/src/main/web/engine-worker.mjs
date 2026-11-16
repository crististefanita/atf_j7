import { analyzeReview, canonicalCheckpoint, continueMatch, matchHistory, openLedger, openMatch, openReview,
  playBot, playMatch, reviewAction, snapshot } from "./chess-engine.mjs";

let pending = Promise.resolve();
self.onmessage = ({ data }) => {
  pending = pending.then(() => respond(data ?? {}));
};

async function respond({ id, type, resources, archive, checkpoint, ledger, legacy, route, slot, archiveId, setup,
  expectedRevision, automatic, action, move, ply = -1 }) {
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
    } else if (type === "ledger-open" && optionalText(ledger) && optionalText(legacy?.local) && optionalText(legacy?.ai)) {
      self.postMessage({ id, ...JSON.parse(openLedger(ledger ?? null, legacy?.local ?? null, legacy?.ai ?? null)) });
    } else if (type === "match-open" && validMatchOpen(route, slot, archiveId, setup)) {
      self.postMessage({ id, ...JSON.parse(openMatch(route ?? null, slot ?? 0, archiveId ?? null,
        setup?.mode ?? null, setup?.humanSide ?? null, setup?.level ?? null)) });
    } else if (type === "match-play" && validRevision(expectedRevision) && typeof move === "string") {
      self.postMessage({ id, ...JSON.parse(playMatch(expectedRevision, move)) });
    } else if (type === "match-bot" && validRevision(expectedRevision)) {
      self.postMessage({ id, ...JSON.parse(playBot(expectedRevision)) });
    } else if (type === "match-history" && validRevision(expectedRevision) && validRevision(ply)) {
      self.postMessage({ id, ...JSON.parse(matchHistory(expectedRevision, ply)) });
    } else if (type === "match-continue" && validRevision(expectedRevision) && typeof automatic === "boolean") {
      self.postMessage({ id, ...JSON.parse(continueMatch(expectedRevision, automatic)) });
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

function optionalText(value) {
  return value == null || typeof value === "string";
}

function validMatchOpen(route, slot, archiveId, setup) {
  if ([route, slot, archiveId, setup].filter(value => value != null).length !== 1) return false;
  if (route != null) return ["LOCAL", "AI"].includes(route);
  if (slot != null) return Number.isInteger(slot) && slot >= 1 && slot <= 10;
  if (archiveId != null) return typeof archiveId === "string" && /^[a-f0-9-]{36}$/.test(archiveId);
  return setup.mode === "LOCAL" || (setup.mode === "AI" && ["WHITE", "BLACK"].includes(setup.humanSide)
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
