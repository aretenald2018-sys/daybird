import { describe, expect, it } from 'vitest';
import { pairingCodeFromUrl } from './dashboard';

describe('DayBird dashboard pairing URL', () => {
  it('accepts only the daybird pair deep link', () => {
    expect(pairingCodeFromUrl('daybird://pair?code=owner.secret')).toBe('owner.secret');
    expect(pairingCodeFromUrl('https://example.com/?code=owner.secret')).toBeNull();
    expect(pairingCodeFromUrl('daybird://pair')).toBeNull();
  });
});
