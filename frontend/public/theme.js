try {
  var theme = localStorage.getItem('theme')
  if (
    theme === 'dark' ||
    (theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches)
  ) {
    document.documentElement.classList.add('dark')
  }
} catch (e) {}
