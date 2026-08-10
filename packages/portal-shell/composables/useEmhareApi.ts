export function useEmhareApi() {
  const auth = useEmhareAuth()
  const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env
  const apiBase = env?.NUXT_PUBLIC_API_BASE ?? 'http://localhost:8080'

  async function request<T>(path: string, options: Parameters<typeof $fetch<T>>[1] = {}) {
    if (!auth.accessToken.value) {
      await auth.requireUser()
    }
    try {
      return await $fetch<T>(`${apiBase}${path}`, {
        ...options,
        headers: {
          ...(options?.headers as Record<string, string> | undefined),
          ...(auth.accessToken.value ? { Authorization: `Bearer ${auth.accessToken.value}` } : {})
        }
      })
    } catch (error) {
      if (statusCode(error) === 401) {
        await auth.restartLogin()
      }
      throw error
    }
  }

  function statusCode(error: unknown) {
    const httpError = error as {
      status?: number
      statusCode?: number
      response?: { status?: number, statusCode?: number }
    }
    return httpError?.response?.status
      ?? httpError?.response?.statusCode
      ?? httpError?.status
      ?? httpError?.statusCode
  }

  function errorMessage(error: unknown, fallback = 'The request could not be completed.') {
    if (error && typeof error === 'object') {
      const fetchError = error as {
        data?: {
          detail?: string
          message?: string
          title?: string
          violations?: Array<{ field?: string, message?: string }>
        }
        message?: string
      }
      const validationMessages = fetchError.data?.violations
        ?.filter(violation => violation.field || violation.message)
        .map((violation) => {
          const fieldLabel = violation.field
            ? violation.field
                .replace(/\[(\d+)\]/g, (_, index: string) => ` ${Number(index) + 1}`)
                .replaceAll('.', ' ')
                .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
                .replace(/^./, firstCharacter => firstCharacter.toUpperCase())
            : 'Value'
          return `${fieldLabel}: ${violation.message ?? 'Invalid value'}`
        })

      if (validationMessages?.length) {
        return validationMessages.length === 1
          ? validationMessages[0] ?? fallback
          : validationMessages.map(message => `• ${message}`).join('\n')
      }

      return fetchError.data?.detail
        ?? fetchError.data?.message
        ?? fetchError.data?.title
        ?? fetchError.message
        ?? fallback
    }
    return fallback
  }

  return { request, errorMessage }
}
