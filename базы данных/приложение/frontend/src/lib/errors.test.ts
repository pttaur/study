import { describe, expect, it } from 'vitest';
import { toErrorMessage } from './errors';

describe('toErrorMessage', () => {
  it('returns the message of an Error', () => {
    expect(toErrorMessage(new Error('boom'))).toBe('boom');
  });

  it('stringifies non-error values', () => {
    expect(toErrorMessage('nope')).toBe('nope');
    expect(toErrorMessage(42)).toBe('42');
  });
});
