import { fetchWithTimeout } from "./utils";

export async function fetchWikipediaSummary(
  title: string,
  languageCode = "en"
): Promise<string> {
  const locales = languageCode === "en" ? ["en"] : [languageCode, "en"];
  for (const locale of locales) {
    const summary = await fetchWikipediaSummaryForLocale(title, locale);
    if (summary) return summary;
  }
  return "";
}

async function fetchWikipediaSummaryForLocale(title: string, locale: string): Promise<string> {
  const url = `https://${locale}.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(title)}`;
  try {
    const response = await fetchWithTimeout(url);
    if (!response.ok) return "";
    const data = await response.json();
    return typeof data.extract === "string" ? data.extract : "";
  } catch {
    return "";
  }
}
