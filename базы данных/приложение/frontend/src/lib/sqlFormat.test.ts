import { describe, expect, it } from 'vitest';
import { formatSql } from './sqlFormat';

describe('formatSql', () => {
  it('breaks generated join SQL into readable lines', () => {
    const sql =
      'SELECT t0."id", t1."name" FROM "a"."s"."t0" t0 ' +
      'INNER JOIN "b"."s"."t1" t1 ON t1."id" = t0."id" ORDER BY t0."id"';

    expect(formatSql(sql)).toBe(
      'SELECT t0."id", t1."name"\n' +
        'FROM "a"."s"."t0" t0\n' +
        'INNER JOIN "b"."s"."t1" t1\n' +
        'ON t1."id" = t0."id"\n' +
        'ORDER BY t0."id"',
    );
  });

  it('keeps plain SQL on one line', () => {
    expect(formatSql('SELECT 1')).toBe('SELECT 1');
  });
});
