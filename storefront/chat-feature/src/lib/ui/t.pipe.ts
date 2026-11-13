import { Pipe, PipeTransform, inject } from '@angular/core';
import { CC_LABEL_RESOLVER } from './labels';

@Pipe({ name: 'ccT', standalone: true })
export class CcTPipe implements PipeTransform
{
  private readonly resolve = inject(CC_LABEL_RESOLVER);
  transform(key: string | undefined, params?: Record<string, string | number>): string { return key ? this.resolve(key, params) : ''; }
}
