import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { Monitor, Moon, Sun } from 'lucide-react'
export type ThemeMode = 'light' | 'dark' | 'system'
const ThemeContext = createContext<{ mode: ThemeMode; setMode: (mode: ThemeMode) => void }>({
  mode: 'system',
  setMode: () => {},
})
export function readTheme(): ThemeMode {
  try {
    const value = localStorage.getItem('voidhaven-theme')
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(readTheme)
  useEffect(() => {
    const media = matchMedia('(prefers-color-scheme: dark)')
    const apply = () => {
      const resolved = mode === 'system' ? (media.matches ? 'dark' : 'light') : mode
      document.documentElement.dataset.theme = resolved
      document.documentElement.style.colorScheme = resolved
      document
        .querySelector('meta[name="theme-color"]')
        ?.setAttribute('content', resolved === 'dark' ? '#151412' : '#f8f7f5')
    }
    apply()
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [mode])
  useEffect(() => {
    const sync = (e: StorageEvent) => {
      if (e.key === 'voidhaven-theme' || e.key === null) setMode(readTheme())
    }
    window.addEventListener('storage', sync)
    return () => window.removeEventListener('storage', sync)
  }, [])
  const change = (next: ThemeMode) => {
    setMode(next)
    try {
      localStorage.setItem('voidhaven-theme', next)
    } catch {
      /* Theme still works in this session. */
    }
  }
  return <ThemeContext.Provider value={{ mode, setMode: change }}>{children}</ThemeContext.Provider>
}
export function ThemeButton() {
  const { mode, setMode } = useContext(ThemeContext)
  return (
    <div className="theme-switch" role="group" aria-label="外观主题">
      {(
        [
          { value: 'light', label: '浅色', icon: Sun },
          { value: 'dark', label: '深色', icon: Moon },
          { value: 'system', label: '跟随系统', icon: Monitor },
        ] as const
      ).map(({ value, label, icon: Icon }) => (
        <button
          key={value}
          type="button"
          title={label}
          aria-label={label}
          aria-pressed={mode === value}
          onClick={() => setMode(value)}
        >
          <Icon size={16} />
        </button>
      ))}
    </div>
  )
}
