import { analyzeRatedReview, analyzeReview, canonicalCheckpoint, continueArchiveReview, continueMatch, matchHistory, openLedger, openMatch, openReview,
  archiveReviewCheckpoint, archiveReviewRated, importLegacyReview, ledgerState, openArchiveReview, playBot, playMatch, reviewAction, reviewGraphSelect,
  saveArchiveReview, snapshot } from "./chess-engine.mjs";

let pending = Promise.resolve();
self.onmessage = ({ data }) => {
  pending = pending.then(() => respond(data ?? {}));
};

async function respond({ id, type, resources, archive, checkpoint, review, ledger, legacy, route, slot, archiveId, setup,
  expectedRevision, automatic, action, move, x, width, rated = false, ply = -1 }) {
  try {
    if (type === "snapshot" && typeof resources === "string") {
      self.postMessage({ id, snapshot: snapshot(resources) });
    } else if (type === "review-open" && validArchive(archive)) {
      const canonical = canonicalCheckpoint(archive.checkpoint);
      const digest = await sha256(canonical);
      const view = JSON.parse(openReview(archive.id, canonical, archive.review, archive.bookmark ?? null, digest));
      self.postMessage({ id, view });
    } else if (type === "archive-review-open" && validArchiveId(archiveId)) {
      const canonical = archiveReviewCheckpoint(archiveId);
      const view = openArchiveReview(archiveId, await sha256(canonical));
      const state = JSON.parse(ledgerState());
      self.postMessage(view === null ? { id, ...state, outcome: "NEEDS_ANALYSIS", checkpoint: canonical, rated: archiveReviewRated(archiveId) }
        : { id, ...state, outcome: "OPENED", view: JSON.parse(view) });
    } else if (type === "archive-review-save" && validArchiveId(archiveId) && typeof checkpoint === "string" && typeof review === "string") {
      const canonical = archiveReviewCheckpoint(archiveId);
      if (checkpoint !== canonical) throw new TypeError("The analysis checkpoint does not match the archived game");
      const view = JSON.parse(saveArchiveReview(archiveId, canonical, review, await sha256(canonical)));
      self.postMessage({ id, ...JSON.parse(ledgerState()), outcome: "OPENED", view });
    } else if (type === "archive-review-continue" && validArchiveId(archiveId) && validRevision(expectedRevision) && typeof automatic === "boolean") {
      self.postMessage({ id, ...JSON.parse(continueArchiveReview(archiveId, expectedRevision, automatic)) });
    } else if (type === "archive-review-migrate" && validArchive(archive)) {
      const canonical = canonicalLegacyCheckpoint(archive.checkpoint);
      const digest = canonical === null ? null : await sha256(canonical);
      self.postMessage({ id, ...JSON.parse(importLegacyReview(archive.id, canonical, archive.review, archive.bookmark ?? null, digest)) });
    } else if (type === "review-action" && validRevision(expectedRevision) && typeof action === "string" && Number.isInteger(ply) &&
        ply >= -1 && ply <= 2147483647 && (archiveId == null || validArchiveId(archiveId))) {
      const result = JSON.parse(reviewAction(archiveId ?? null, expectedRevision, action, ply));
      postReview(id, archiveId, result);
    } else if (type === "review-graph-select" && validRevision(expectedRevision) && Number.isFinite(x) &&
        Number.isFinite(width) && width > 4 && (archiveId == null || validArchiveId(archiveId))) {
      const result = JSON.parse(reviewGraphSelect(archiveId ?? null, expectedRevision, x, width));
      postReview(id, archiveId, result);
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
    } else if (type === "review-analyze" && typeof checkpoint === "string" && typeof rated === "boolean") {
      const canonical = canonicalCheckpoint(checkpoint);
      const digest = await sha256(canonical);
      const analyze = rated ? analyzeRatedReview : analyzeReview;
      const review = analyze(canonical, digest, (progress, total) => self.postMessage({ id, progress, total }));
      self.postMessage({ id, archive: { id: crypto.randomUUID(), checkpoint: canonical, review } });
    } else {
      throw new TypeError("Invalid chess worker request");
    }
  } catch (failure) {
    self.postMessage({ id, error: String(failure) });
  }
}

function postReview(id, archiveId, result) {
  const state = archiveId == null ? {} : JSON.parse(ledgerState());
  self.postMessage({ id, ...state, ...result });
}

function validRevision(value) {
  return Number.isInteger(value) && value >= 0 && value <= 2147483647;
}

function optionalText(value) {
  return value == null || typeof value === "string";
}

function validArchiveId(value) {
  return typeof value === "string" && /^[a-f0-9-]{36}$/.test(value);
}

function validMatchOpen(route, slot, archiveId, setup) {
  if ([route, slot, archiveId, setup].filter(value => value != null).length !== 1) return false;
  if (route != null) return ["LOCAL", "AI"].includes(route);
  if (slot != null) return Number.isInteger(slot) && slot >= 1 && slot <= 10;
  if (archiveId != null) return validArchiveId(archiveId);
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

function canonicalLegacyCheckpoint(checkpoint) {
  try {
    return canonicalCheckpoint(checkpoint);
  } catch {
    return null;
  }
}
