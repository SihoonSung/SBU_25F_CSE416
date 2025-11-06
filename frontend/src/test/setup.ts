import '@testing-library/jest-dom'

// Polyfill for TextEncoder/TextDecoder in jsdom
if (typeof global.TextEncoder === 'undefined') {
  const { TextEncoder, TextDecoder } = require('util')
  global.TextEncoder = TextEncoder
  global.TextDecoder = TextDecoder
}

// Mock import.meta for Vite in Jest environment
if (typeof global.import === 'undefined') {
  (global as any).import = {
    meta: {
      env: {
        VITE_BACKEND_URL: 'http://localhost:8080'
      }
    }
  }
}

