import { describe, expect, it } from 'vitest';
import { formatCell } from './format';

describe('formatCell', () => {
  it('renders null and undefined as ∅', () => {
    expect(formatCell(null)).toBe('∅');
    expect(formatCell(undefined)).toBe('∅');
  });

  it('stringifies scalars', () => {
    expect(formatCell(42)).toBe('42');
    expect(formatCell('x')).toBe('x');
    expect(formatCell(false)).toBe('false');
  });

  it('json-encodes objects', () => {
    expect(formatCell({ a: 1 })).toBe('{"a":1}');
  });
});
