import { describe, expect, it } from 'vitest';
import { en } from './en';
import { es } from './es';

describe('translations', () => {
  it('Spanish defines exactly the English keys', () => {
    expect(Object.keys(es).sort()).toEqual(Object.keys(en).sort());
  });
  it('no translation is empty', () => {
    for (const [k, v] of Object.entries(es)) {
      expect(v.trim().length, k).toBeGreaterThan(0);
    }
  });
});
