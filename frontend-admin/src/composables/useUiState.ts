import { ref } from 'vue';
import { normalizeApiError } from '../utils/format';

export function useUiState() {
  const loading = ref(false);
  const error = ref('');
  const toast = ref('');

  function showToast(message: string) {
    toast.value = message;
    window.setTimeout(() => {
      if (toast.value === message) toast.value = '';
    }, 1800);
  }

  async function runTask(task: () => Promise<void>) {
    loading.value = true;
    error.value = '';
    try {
      await task();
    } catch (err) {
      error.value = err instanceof Error ? err.message : '操作失败，请稍后重试';
    } finally {
      loading.value = false;
    }
  }

  return {
    loading,
    error,
    toast,
    displayError: () => normalizeApiError(error.value),
    showToast,
    runTask
  };
}
