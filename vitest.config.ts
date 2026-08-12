// Author: Tinashe K

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'happy-dom',
    include: [
      'apps/**/tests/unit/**/*.{test,spec}.ts',
      'packages/**/tests/unit/**/*.{test,spec}.ts',
      'tests/unit/**/*.{test,spec}.ts'
    ],
    passWithNoTests: true,
    coverage: {
      provider: 'v8',
      reportsDirectory: 'coverage/frontend',
      reporter: ['text-summary', 'html', 'lcov'],
      include: [
        'apps/**/*.{ts,vue}',
        'packages/**/*.{ts,vue}'
      ],
      exclude: [
        '**/.nuxt/**',
        '**/.output/**',
        '**/coverage/**',
        '**/dist/**',
        '**/node_modules/**',
        '**/*.{test,spec}.ts',
        '**/*.d.ts'
      ]
    }
  }
})
