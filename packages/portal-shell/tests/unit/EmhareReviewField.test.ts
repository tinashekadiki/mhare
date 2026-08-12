// Author: Tinashe K

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EmhareReviewField from '../../components/data-display/EmhareReviewField.vue'

describe('EmhareReviewField', () => {
  it('shows a clear fallback when no value is supplied', () => {
    const wrapper = mount(EmhareReviewField, {
      props: { label: 'National ID' }
    })

    expect(wrapper.get('dt').text()).toBe('National ID')
    expect(wrapper.get('dd').text()).toBe('Not provided')
  })

  it('renders the supplied value and wide layout through public props', () => {
    const wrapper = mount(EmhareReviewField, {
      props: {
        label: 'Programme',
        value: 'Computer Science',
        wide: true
      }
    })

    expect(wrapper.get('dd').text()).toBe('Computer Science')
    expect(wrapper.classes()).toContain('md:col-span-2')
  })
})
