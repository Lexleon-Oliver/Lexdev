/** Calcula em unidades inteiras para que o arredondamento coincida com BigDecimal/HALF_UP. */
const INDIVISIBLE = new Set(['UN', 'CX', 'PC', 'PAR', 'RL', 'FD']);

function scaled(value: number, places: number): bigint | null {
  if (!Number.isFinite(value) || value < 0) return null;
  const match = String(value).match(/^(\d+)(?:\.(\d+))?$/);
  if (!match) return null;
  const fraction = match[2] ?? '';
  if (fraction.length > places && /[1-9]/.test(fraction.slice(places))) return null;
  return BigInt(match[1]) * (10n ** BigInt(places))
    + BigInt((fraction.slice(0, places)).padEnd(places, '0') || '0');
}

export function validSaleQuantity(unit: string, quantity: number): boolean {
  if (!Number.isFinite(quantity) || quantity <= 0) return false;
  const normalized = (unit ?? '').trim().toUpperCase();
  const precision = INDIVISIBLE.has(normalized) ? 0 : normalized === 'KG' ? 3 : 4;
  return scaled(quantity, precision) !== null;
}

export function quantityStepFor(unit: string): number {
  const normalized = (unit ?? '').trim().toUpperCase();
  return INDIVISIBLE.has(normalized) ? 1 : normalized === 'KG' ? 0.001 : 0.0001;
}

export function validUnitPrice(value: number): boolean {
  return value > 0 && scaled(value, 4) !== null;
}

export function moneyCents(value: number): number | null {
  const cents = scaled(value, 2);
  return cents !== null && cents <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(cents) : null;
}

export function lineGrossCents(quantity: number, unitPrice: number): number | null {
  const q = scaled(quantity, 4);
  const p = scaled(unitPrice, 4);
  if (q === null || p === null) return null;
  // Preço e quantidade em 4 casas -> produto em 8 casas -> centavos em 2 casas.
  const cents = (q * p + 500_000n) / 1_000_000n;
  return cents <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(cents) : null;
}
