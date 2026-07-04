// Function: Basic App component test
// Time: 2026-07-03
// Author: AxeXie
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('renders without errors', () => {
    const wrapper = mount(App, {
      global: {
        stubs: ['router-view']
      }
    })
    expect(wrapper.exists()).toBe(true)
  })
})
