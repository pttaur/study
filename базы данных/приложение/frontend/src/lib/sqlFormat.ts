const BREAK_BEFORE = [
  'FULL JOIN',
  'RIGHT JOIN',
  'LEFT JOIN',
  'INNER JOIN',
  'FROM',
  'ON',
  'WHERE',
  'GROUP BY',
  'ORDER BY',
  'LIMIT',
  'OFFSET',
];

/** Разбивает сгенерированный SQL на строки по ключевым словам для читаемого вывода. */
export function formatSql(sql: string): string {
  let result = sql.trim();
  for (const keyword of BREAK_BEFORE) {
    const pattern = new RegExp(`\\s+${keyword.replace(/ /g, '\\s+')}\\s+`, 'gi');
    result = result.replace(pattern, `\n${keyword} `);
  }
  return result;
}

/** Локальное человекочитаемое время; исходная строка, если дата невалидна. */
export function formatTimestamp(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ru-RU');
}
