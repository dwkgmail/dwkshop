export type ValidationResult = {
  valid: boolean;
  message?: string;
};

export function validateAdminLogin(username: string, password: string): ValidationResult {
  if (!username.trim() || !password.trim()) {
    return { valid: false, message: 'Please enter username and password' };
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

export function validateShipment(logisticsCompany: string, logisticsNo: string): ValidationResult {
  if (!logisticsCompany.trim() || !logisticsNo.trim()) {
    return { valid: false, message: 'Please enter logistics company and tracking number' };
  }
  return { valid: true };
}

export function validateProductName(name: string): ValidationResult {
  if (!name.trim()) {
    return { valid: false, message: 'Please enter product name' };
  }
  return { valid: true };
}

export function validateAdminMember(mobile: string, password: string): ValidationResult {
  if (!/^1\d{10}$/.test(mobile.trim())) {
    return { valid: false, message: 'Please enter a valid mobile number' };
  }
  if (password.trim().length < 6) {
    return { valid: false, message: 'Password must be at least 6 characters' };
  }
  return { valid: true };
}
