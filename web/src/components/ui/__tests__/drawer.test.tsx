// @vitest-environment jsdom
import '@testing-library/jest-dom/vitest'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import {
  ResponsiveModal,
  ResponsiveModalContent,
  ResponsiveModalHeader,
  ResponsiveModalTitle,
  ResponsiveModalDescription,
} from '../drawer'

describe('ResponsiveModal / Drawer', () => {
  beforeEach(() => {
    // Mock do ResizeObserver para jsdom
    global.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
  })

  it('renderiza como Dialog no modo Desktop (>= 640px)', () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false, // Desktop
      media: query,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))

    render(
      <ResponsiveModal open={true}>
        <ResponsiveModalContent>
          <ResponsiveModalHeader>
            <ResponsiveModalTitle>Título Desktop</ResponsiveModalTitle>
            <ResponsiveModalDescription>Descrição Desktop</ResponsiveModalDescription>
          </ResponsiveModalHeader>
          <div>Conteúdo do Modal</div>
        </ResponsiveModalContent>
      </ResponsiveModal>
    )

    expect(screen.getByText('Título Desktop')).toBeInTheDocument()
    expect(screen.getByText('Descrição Desktop')).toBeInTheDocument()
    expect(screen.getByText('Conteúdo do Modal')).toBeInTheDocument()
  })

  it('renderiza como Drawer com Handle no modo Mobile (< 640px)', () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: true, // Mobile
      media: query,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))

    render(
      <ResponsiveModal open={true}>
        <ResponsiveModalContent showHandle={true}>
          <ResponsiveModalHeader>
            <ResponsiveModalTitle>Título Mobile</ResponsiveModalTitle>
            <ResponsiveModalDescription>Descrição Mobile</ResponsiveModalDescription>
          </ResponsiveModalHeader>
          <div>Conteúdo Bottom Sheet</div>
        </ResponsiveModalContent>
      </ResponsiveModal>
    )

    expect(screen.getByText('Título Mobile')).toBeInTheDocument()
    expect(screen.getByText('Descrição Mobile')).toBeInTheDocument()
    expect(screen.getByText('Conteúdo Bottom Sheet')).toBeInTheDocument()
    expect(document.querySelector('[data-slot="drawer-handle"]')).toBeInTheDocument()
  })
})
