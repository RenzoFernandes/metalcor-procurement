import { useCallback, useEffect, useState } from 'react'

interface ApiState<T> {
  data: T | null
  error: unknown
  loading: boolean
  reload: () => void
  setData: (data: T) => void
}

/** Runs a GET when `key` changes. Ignores results that arrive after the component moved on. */
export function useApi<T>(fetcher: () => Promise<T>, key: unknown): ApiState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetcher().then(
      (result) => {
        if (cancelled) return
        setData(result)
        setLoading(false)
      },
      (err: unknown) => {
        if (cancelled) return
        setData(null)
        setError(err)
        setLoading(false)
      },
    )
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, tick])

  const reload = useCallback(() => setTick((n) => n + 1), [])
  return { data, error, loading, reload, setData }
}