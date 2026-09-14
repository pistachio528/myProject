import { computed } from 'vue'

export function useAuth() {
  const isLoggedIn = computed(() => {
    return !!localStorage.getItem('authorization')
  })

  return { isLoggedIn }
}
