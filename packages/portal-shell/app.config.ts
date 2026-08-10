export default defineAppConfig({
  ui: {
    colors: {
      primary: 'uzgreen',
      secondary: 'uzgold',
      success: 'green',
      info: 'blue',
      warning: 'amber',
      error: 'red',
      neutral: 'slate'
    },
    button: {
      defaultVariants: {
        size: 'md',
        color: 'neutral',
        variant: 'outline'
      }
    },
    select: {
      slots: {
        base: 'min-h-8'
      }
    },
    selectMenu: {
      slots: {
        base: 'min-h-8'
      }
    }
  }
})
