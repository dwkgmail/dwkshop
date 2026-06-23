export function normalizeApiError(message: string) {
  return message.startsWith('请求失败 (500)') ? '请求失败（500），请稍后重试' : message;
}

export function productVisualTone(id: number) {
  const tones = ['tone-orange', 'tone-green', 'tone-blue', 'tone-dark', 'tone-pink'];
  return tones[id % tones.length];
}
