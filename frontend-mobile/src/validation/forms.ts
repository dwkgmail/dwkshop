export type ValidationResult = {
  valid: boolean;
  message?: string;
};

export function validateRequiredCredentials(mobile: string, password: string): ValidationResult {
  if (!mobile.trim() || !password.trim()) {
    return { valid: false, message: 'Please enter mobile and password' };
  }
  return { valid: true };
}

export function validatePasswordChange(oldPassword: string, newPassword: string, confirmPassword: string): ValidationResult {
  if (!oldPassword.trim() || !newPassword.trim()) {
    return { valid: false, message: 'Please enter old and new password' };
  }
  if (newPassword.trim() !== confirmPassword.trim()) {
    return { valid: false, message: 'New passwords do not match' };
  }
  return { valid: true };
}
