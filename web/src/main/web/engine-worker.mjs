import { analyzeRatedReview, analyzeReview, applyBotMove, canonicalCheckpoint, computeBotMove, continueArchiveReview, continueMatch,
  flipMatchBoard, matchAutomation, matchDecision, matchHistory, normalizePlayerName, openLedger, openMatch, openReview, archiveReviewCheckpoint,
  archiveReviewRated, importLegacyReview, ledgerState, matchAnalysisAction, openArchiveReview, openMatchAnalysis, pieceArtwork, playBot, playMatch, reviewAction, reviewGraphSelect,
  saveArchiveReview, snapshot, continueStudyMatch, openStudyReview, studyCommand, studyReviewAction, studyReviewGraphSelect } from "./chess-engine.mjs";

let pending = Promise.resolve();
self.onmessage = ({ data }) => {
  pending = pending.then(() => respond(data ?? {}));
};

async function respond({ id, type, resources, archive, checkpoint, review, ledger, legacy, route, slot, archiveId, setup,
  expectedRevision, automatic, action, move, x, width, name, rated = false, ply = -1, studyReview = false, blackBelow = false,
  feature, value, index, saved, option, locale }) {
  try {
    if (type === "snapshot" && typeof resources === "string") {
      self.postMessage({ id, snapshot: snapshot(resources) });
    } else if (type === "study" && [feature, action, value, saved, option, locale].every(field => typeof field === "string") && validPly(index)) {
      self.postMessage({ id, study: JSON.parse(studyCommand(feature, action, value, index, saved, option, locale)) });
    } else if (type === "profile-name" && typeof name === "string") {
      self.postMessage({ id, name: normalizePlayerName(name) });
    } else if (type === "review-open" && validArchive(archive)) {
      const canonical = canonicalCheckpoint(archive.checkpoint);
      const digest = await sha256(canonical);
      const view = JSON.parse(openReview(archive.id, canonical, archive.review, archive.bookmark ?? null, digest));
      self.postMessage({ id, view });
    } else if (type === "study-review-open" && validArchive(archive) && validPly(ply) && typeof blackBelow === "boolean") {
      const canonical = canonicalCheckpoint(archive.checkpoint);
      const digest = await sha256(canonical);
      self.postMessage({ id, view: JSON.parse(openStudyReview(archive.id, canonical, archive.review, digest, blackBelow, ply)) });
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
      const result = JSON.parse(studyReview === true ? studyReviewAction(expectedRevision, action, ply)
        : reviewAction(archiveId ?? null, expectedRevision, action, ply));
      postReview(id, archiveId, result);
    } else if (type === "review-graph-select" && validRevision(expectedRevision) && Number.isFinite(x) &&
        Number.isFinite(width) && width > 4 && (archiveId == null || validArchiveId(archiveId))) {
      const result = JSON.parse(studyReview === true ? studyReviewGraphSelect(expectedRevision, x, width)
        : reviewGraphSelect(archiveId ?? null, expectedRevision, x, width));
      postReview(id, archiveId, result);
    } else if (type === "ledger-open" && optionalText(ledger) && optionalText(legacy?.local) && optionalText(legacy?.ai)) {
      self.postMessage({ id, ...JSON.parse(openLedger(ledger ?? null, legacy?.local ?? null, legacy?.ai ?? null)), pieceArt: JSON.parse(pieceArtwork()) });
    } else if (type === "match-open" && validMatchOpen(route, slot, archiveId, setup)) {
      self.postMessage({ id, ...JSON.parse(openMatch(route ?? null, slot ?? 0, archiveId ?? null,
        setup?.mode ?? null, setup?.humanSide ?? null, setup?.level ?? null, setup?.analysis === true)) });
    } else if (type === "match-play" && validRevision(expectedRevision) && typeof move === "string") {
      self.postMessage({ id, ...JSON.parse(playMatch(expectedRevision, move)) });
    } else if (type === "match-bot" && validRevision(expectedRevision)) {
      self.postMessage({ id, ...JSON.parse(playBot(expectedRevision)) });
    } else if (type === "match-bot-compute" && typeof checkpoint === "string") {
      self.postMessage({ id, move: computeBotMove(checkpoint) });
    } else if (type === "match-analysis-open" && typeof checkpoint === "string" && validRevision(ply) && validRevision(expectedRevision)) {
      self.postMessage({ id, ...JSON.parse(openMatchAnalysis(checkpoint, ply, expectedRevision)) });
    } else if (type === "match-analysis-action" && validRevision(expectedRevision) && validMatchAnalysisAction(action, index)) {
      self.postMessage({ id, ...JSON.parse(matchAnalysisAction(expectedRevision, action, index ?? 0)) });
    } else if (type === "match-bot-apply" && validRevision(expectedRevision) && typeof move === "string") {
      self.postMessage({ id, ...JSON.parse(applyBotMove(expectedRevision, move)) });
    } else if (type === "match-automation" && validRevision(expectedRevision) && typeof automatic === "boolean") {
      self.postMessage({ id, ...JSON.parse(matchAutomation(expectedRevision, automatic)) });
    } else if (type === "match-decision" && validRevision(expectedRevision) && ["OFFER_DRAW", "RESIGN"].includes(action)) {
      self.postMessage({ id, ...JSON.parse(matchDecision(expectedRevision, action)) });
    } else if (type === "match-history" && validRevision(expectedRevision) && validRevision(ply)) {
      self.postMessage({ id, ...JSON.parse(matchHistory(expectedRevision, ply)) });
    } else if (type === "match-flip" && validRevision(expectedRevision)) {
      self.postMessage({ id, ...JSON.parse(flipMatchBoard(expectedRevision)) });
    } else if (type === "match-continue" && validRevision(expectedRevision) && typeof automatic === "boolean") {
      self.postMessage({ id, ...JSON.parse(continueMatch(expectedRevision, automatic)) });
    } else if (type === "study-match-continue" && typeof checkpoint === "string" && typeof automatic === "boolean" && typeof blackBelow === "boolean") {
      self.postMessage({ id, ...JSON.parse(continueStudyMatch(checkpoint, automatic, blackBelow)) });
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

function validPly(value) { return value === -1 || validRevision(value); }

function validMatchAnalysisAction(action, index) {
  if (action === "preview-branch" || action === "preview-seek") return validRevision(index);
  return ["hint", "preview", "preview-close", "preview-proof"].includes(action) && (index == null || validRevision(index));
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
  return setup.mode === "LOCAL" || (setup.mode === "AI" && ["WHITE", "BLACK", "RANDOM"].includes(setup.humanSide)
    && ["BASIC", "ADVANCED", "HARD"].includes(setup.level) && (setup.analysis === undefined || typeof setup.analysis === "boolean"));
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
