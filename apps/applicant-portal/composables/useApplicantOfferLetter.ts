import type { AdmissionOfferSummary } from '@emhare/portal-shell/types/admissions'

/** Opens only the applicant's current published offer letter. @author Tinashe K */
export function useApplicantOfferLetter() {
  const api = useEmhareApi()
  const { showError } = useEmhareConfirm()
  const openingOfferId = ref<string | null>(null)

  async function openOfferLetter(
    offer: AdmissionOfferSummary | null,
    disposition: 'inline' | 'attachment',
  ) {
    if (!offer?.currentPublicationId) {
      await showError(
        'Offer letter is not available',
        'Admissions has not published the current offer letter yet.',
      )
      return
    }

    const offerLetterWindow = window.open('about:blank', '_blank')
    if (!offerLetterWindow) {
      await showError(
        'Offer letter could not be opened',
        'Allow pop-ups for eMhare, then try again.',
      )
      return
    }

    offerLetterWindow.opener = null
    openingOfferId.value = offer.id
    try {
      const access = await api.request<{ generatedDocumentId: string }>(
        `/api/admissions/applicant/offers/${offer.id}/published-document`,
      )
      const document = await api.request<{ downloadUrl: string }>(
        `/api/documents/${access.generatedDocumentId}/applicant-download?disposition=${disposition}`,
      )
      offerLetterWindow.location.href = document.downloadUrl
    } catch (error) {
      offerLetterWindow.close()
      await showError('Offer letter could not be opened', api.errorMessage(error))
    } finally {
      openingOfferId.value = null
    }
  }

  return { openingOfferId, openOfferLetter }
}
