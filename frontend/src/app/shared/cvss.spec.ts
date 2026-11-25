import { describe, expect, it } from 'vitest';
import { cvss31 } from './cvss';

describe('cvss31 preview', () => {
  it('matches the reference scores used by the backend tests', () => {
    expect(cvss31('CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H')).toBe(9.8);
    expect(cvss31('CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N')).toBe(6.1);
    expect(cvss31('CVSS:3.1/AV:P/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N')).toBe(1.6);
  });
  it('returns null for invalid vectors', () => {
    expect(cvss31('CVSS:3.0/AV:N')).toBeNull();
  });
});
