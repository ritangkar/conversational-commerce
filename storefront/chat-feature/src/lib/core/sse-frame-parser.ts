/**
 * Incremental parser for text/event-stream. Handles frames split across chunks, CRLF, comment lines (heartbeats),
 * multi-line data and unknown fields. EventSource is not used because it cannot send the bearer header.
 */
export interface SseFrame { event: string; data: string; }

export class SseFrameParser
{
  private buffer = '';
  private event = '';
  private data: string[] = [];
  /** Number of comment lines seen (": hb"); the watchdog resets on any bytes, this is exposed for tests. */
  heartbeats = 0;

  push(chunk: string): SseFrame[]
  {
    this.buffer += chunk;
    const frames: SseFrame[] = [];
    let index: number;
    while ((index = this.nextLineEnd()) >= 0)
    {
      const raw = this.buffer.slice(0, index);
      const skip = this.buffer.startsWith('\r\n', index) ? 2 : 1;
      this.buffer = this.buffer.slice(index + skip);
      const line = raw.endsWith('\r') ? raw.slice(0, -1) : raw;
      if (line === '')
      {
        if (this.data.length > 0 || this.event !== '')
        {
          frames.push({ event: this.event || 'message', data: this.data.join('\n') });
        }
        this.event = '';
        this.data = [];
      }
      else if (line.startsWith(':'))
      {
        this.heartbeats++;
      }
      else
      {
        const colon = line.indexOf(':');
        const field = colon < 0 ? line : line.slice(0, colon);
        let value = colon < 0 ? '' : line.slice(colon + 1);
        if (value.startsWith(' '))
        {
          value = value.slice(1);
        }
        if (field === 'event')
        {
          this.event = value;
        }
        else if (field === 'data')
        {
          this.data.push(value);
        }
      }
    }
    return frames;
  }

  private nextLineEnd(): number
  {
    const n = this.buffer.indexOf('\n');
    return n;
  }
}
