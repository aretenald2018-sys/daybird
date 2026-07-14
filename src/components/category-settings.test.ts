import { describe, expect, it } from 'vitest';
import { DEFAULT_CATEGORIES, reorderActiveCategories } from '../lib/domain';

describe('category ordering', () => {
  it('moves a category and normalizes every visible order', () => {
    const moved = reorderActiveCategories(DEFAULT_CATEGORIES, 'study', -1);

    expect(moved.map(category => category.id).slice(0, 3)).toEqual(['study', 'work', 'meal']);
    expect(moved.map(category => category.order)).toEqual(moved.map((_, index) => index));
  });

  it('keeps the order unchanged at the list boundary', () => {
    const moved = reorderActiveCategories(DEFAULT_CATEGORIES, 'work', -1);

    expect(moved.map(category => category.id)).toEqual(DEFAULT_CATEGORIES.map(category => category.id));
  });
});
