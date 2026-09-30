const PREFIX = "codec-arcade-v1";

function readRecords() {
  try {
    const value = window.localStorage.getItem(PREFIX);
    const parsed = value ? JSON.parse(value) : {};
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (_) {
    return {};
  }
}

function writeRecords(records) {
  try {
    window.localStorage.setItem(PREFIX, JSON.stringify(records));
    return true;
  } catch (_) {
    return false;
  }
}

export function bestFor(game) {
  const value = Number(readRecords()[game]?.best || 0);
  return Number.isFinite(value) ? value : 0;
}

export function updateBest(game, score) {
  const safeScore = Math.max(0, Math.floor(Number(score) || 0));
  const records = readRecords();
  const current = Math.max(0, Math.floor(Number(records[game]?.best) || 0));
  const next = Math.max(current, safeScore);
  records[game] = { ...(records[game] || {}), best: next };
  writeRecords(records);
  return next;
}

export function statFor(game, stat) {
  const value = Number(readRecords()[game]?.[stat] || 0);
  return Number.isFinite(value) ? value : 0;
}

export function incrementStat(game, stat) {
  const records = readRecords();
  const record = records[game] || {};
  const next = Math.max(0, Math.floor(Number(record[stat]) || 0)) + 1;
  records[game] = { ...record, [stat]: next };
  writeRecords(records);
  return next;
}
