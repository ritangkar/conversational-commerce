import http from 'node:http';
import type { AddressInfo } from 'node:net';

/** Fake OCC backend for SC-01 (dummy data). Modes simulate the failure paths the client must survive. */
export type Mode = 'ok' | 'stream500' | 'noDone' | 'silent' | 'slow';

const BLOCKS = [
  { blockId: 'b-due', type: 'recommendationList', estimate: true,
    recommendations: [{ code: 'TY-1001', name: 'Passenger tyre 205/55 R16', quantity: 40, reasonKey: 'DUE', estimate: true }] },
  { blockId: 'b-text', type: 'text', text: 'Based on your order history, 1 items look due for reordering (0 overdue). These are estimates from your usual rhythm.' }
];

export interface Backend { url: string; seen: { auth?: string; path: string }[]; mode: Mode; close: () => Promise<void>; }

export async function startBackend(): Promise<Backend>
{
  const seen: Backend['seen'] = [];
  const state = { mode: 'ok' as Mode };
  const server = http.createServer((req, res) =>
  {
    let body = '';
    req.on('data', c => (body += c));
    req.on('end', async () =>
    {
      seen.push({ auth: req.headers.authorization, path: req.url ?? '' });
      if (req.url?.endsWith('/chat'))
      {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ responseId: 'r-fallback', blocks: BLOCKS }));
        return;
      }
      if (state.mode === 'stream500')
      {
        res.writeHead(500);
        res.end();
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' });
      const send = (event: string, data: object) => res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      if (state.mode === 'silent')
      {
        return; // never writes: the watchdog must fire
      }
      send('step_started', { stepId: 's1', labelKey: 'step.checkingHistory' });
      res.write(': hb\n\n');
      await new Promise(r => setTimeout(r, state.mode === 'slow' ? 120 : 30));
      send('step_completed', { stepId: 's1', labelKey: 'step.checkingHistory', status: 'ok' });
      send('block', { block: { ...BLOCKS[0] } });
      await new Promise(r => setTimeout(r, state.mode === 'slow' ? 120 : 30));
      send('block', { block: { ...BLOCKS[1], text: '' } });
      const words = BLOCKS[1].text!.split(/(?<=\s)/);
      for (let i = 0; i < words.length; i += 3)
      {
        send('text_delta', { stepId: 'b-text', delta: words.slice(i, i + 3).join('') });
      }
      if (state.mode !== 'noDone')
      {
        send('done', {});
      }
      res.end();
    });
  });
  await new Promise<void>(r => server.listen(0, '127.0.0.1', r));
  const port = (server.address() as AddressInfo).port;
  return {
    url: `http://127.0.0.1:${port}/occ/v2/site/users/current`, seen,
    get mode() { return state.mode; }, set mode(m: Mode) { state.mode = m; },
    close: () => new Promise<void>(r => server.close(() => r())) };
}
