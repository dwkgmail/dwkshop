import { describe, expect, it } from 'vitest';
import { validateAdminLogin, validateProductName, validateShipment } from '../../src/validation/forms';

describe('admin form validation', () => {
  it('requires admin credentials', () => {
    expect(validateAdminLogin('', 'admin123')).toEqual({
      valid: false,
      message: 'Please enter username and password'
    });
  });

  it('requires product name', () => {
    expect(validateProductName('  ')).toEqual({
      valid: false,
      message: 'Please enter product name'
    });
  });

  it('requires shipping fields together', () => {
    expect(validateShipment('SF Express', '')).toEqual({
      valid: false,
      message: 'Please enter logistics company and tracking number'
    });
  });
});
