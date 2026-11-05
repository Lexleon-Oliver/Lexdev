import { describe, expect, it } from 'vitest';
import { lineGrossCents, moneyCents, quantityStepFor, validSaleQuantity, validUnitPrice } from './commerce-pricing';

describe('commerce-pricing', () => {
  it('calculates integer cents using decimal rounding identical to HALF_UP', () => {
    expect(lineGrossCents(0.125, 10.60)).toBe(133);
    expect(lineGrossCents(0.350, 40)).toBe(1400);
    expect(lineGrossCents(1.250, 40)).toBe(5000);
    expect(lineGrossCents(0.001, 5)).toBe(1);
    expect(lineGrossCents(0.001, 1)).toBe(0);
  });

  it('allows exactly three decimals in kilograms and integer quantities in UN/CX/PAR', () => {
    expect(validSaleQuantity('KG', 0.125)).toBe(true);
    expect(validSaleQuantity('KG', 0.0001)).toBe(false);
    expect(validSaleQuantity('UN', 1.5)).toBe(false);
    expect(validSaleQuantity('CX', 1.5)).toBe(false);
    expect(validSaleQuantity('L', 0.1254)).toBe(true);
    expect(quantityStepFor('KG')).toBe(0.001);
    expect(quantityStepFor('CX')).toBe(1);
    expect(quantityStepFor('L')).toBe(0.0001);
  });

  it('requires cents for discounts and accepts 4-decimal unit prices', () => {
    expect(validUnitPrice(10.1234)).toBe(true);
    expect(validUnitPrice(10.12345)).toBe(false);
    expect(moneyCents(1.33)).toBe(133);
    expect(moneyCents(1.325)).toBeNull();
  });
});
