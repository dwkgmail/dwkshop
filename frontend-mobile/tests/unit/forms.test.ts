import { describe, expect, it } from 'vitest';
import { validatePasswordChange, validateRequiredCredentials, validateUserRegistration } from '../../src/validation/forms';

describe('mobile form validation', () => {
  it('requires login credentials', () => {
    expect(validateRequiredCredentials('', 'user123')).toEqual({
      valid: false,
      message: 'Please enter mobile and password'
    });
  });

  it('validates password confirmation', () => {
    expect(validatePasswordChange('old', 'new-a', 'new-b')).toEqual({
      valid: false,
      message: 'New passwords do not match'
    });
  });

  it('validates user registration', () => {
    expect(validateUserRegistration('13800000003', 'user123')).toEqual({ valid: true });
    expect(validateUserRegistration('12345', 'user123')).toEqual({
      valid: false,
      message: 'Please enter a valid mobile number'
    });
  });
});
