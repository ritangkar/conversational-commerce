const IDENTIFIER = /[A-Z]{2,4}-\d{3,6}|\d[\d,.]*/;

/** Applies the pack's terminology to display text. Identifiers (codes, numbers) are never translated or replaced. */
export function applyTerminology(text: string, terms: Record<string, string> | undefined): string
{
  if (!terms || !text)
  {
    return text;
  }
  return text.split(/(\s+)/).map(token =>
  {
    if (IDENTIFIER.test(token))
    {
      return token;
    }
    const bare = token.replace(/[^\p{L}]/gu, '');
    const replacement = Object.entries(terms).find(([from]) => from.toLowerCase() === bare.toLowerCase());
    return replacement ? token.replace(bare, replacement[1]) : token;
  }).join('');
}
