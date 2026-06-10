const VAGUE_PATTERNS = /\b(beautiful|amazing|stunning|breathtaking|must-see|incredible|wonderful)\b/i;

export function containsVagueLanguage(text: string): boolean {
  if (!text || text.trim().length === 0) return true;
  const hasVague = VAGUE_PATTERNS.test(text);
  if (!hasVague) return false;
  const hasNamedEntity = /[A-Z][a-z]+/.test(text) || /\d/.test(text);
  return !hasNamedEntity;
}

export function validateOverview(overview: string): boolean {
  if (!overview || overview.length < 120 || overview.length > 500) return false;
  return !containsVagueLanguage(overview);
}

export function validateTips(tips: string[]): boolean {
  if (!tips || tips.length < 5) return false;
  const vagueCount = tips.filter(containsVagueLanguage).length;
  return vagueCount < tips.length / 2;
}

export function validateHighlights(highlights: string[]): boolean {
  if (!highlights || highlights.length < 3) return false;
  return highlights.some((h) => h.length > 10 && !containsVagueLanguage(h));
}

export function validatePackageExtras(extras: {
  overview: string;
  tips: string[];
  highlights: string[];
}): boolean {
  return (
    validateOverview(extras.overview) &&
    validateTips(extras.tips) &&
    validateHighlights(extras.highlights)
  );
}

export function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export function dedupeByTitle<T extends { title: string }>(items: T[]): T[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = normalizeTitle(item.title);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
