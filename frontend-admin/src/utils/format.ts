export function formatCents(cents: number) {
  return (cents / 100).toFixed(2).replace(/\.?0+$/, '');
}

export function normalizeApiError(message: string) {
  return message.startsWith('请求失败 (500)') ? '请求失败（500），请稍后重试' : message;
}
