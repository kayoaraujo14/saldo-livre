import { useEffect, useState } from 'react'
import { formatBRL } from '@/lib/utils'

interface NumberTickerProps {
  value: number
  isCurrency?: boolean
  prefix?: string
  suffix?: string
  duration?: number
  className?: string
}

export function NumberTicker({
  value,
  isCurrency = true,
  prefix = '',
  suffix = '',
  duration = 600,
  className = '',
}: NumberTickerProps) {
  const [displayValue, setDisplayValue] = useState(0)

  useEffect(() => {
    let startTimestamp: number | null = null
    const startValue = displayValue

    const step = (timestamp: number) => {
      if (!startTimestamp) startTimestamp = timestamp
      const progress = Math.min((timestamp - startTimestamp) / duration, 1)
      // Ease out cubic
      const easeProgress = 1 - Math.pow(1 - progress, 3)
      const current = startValue + (value - startValue) * easeProgress
      setDisplayValue(current)

      if (progress < 1) {
        requestAnimationFrame(step)
      } else {
        setDisplayValue(value)
      }
    }

    const frameId = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frameId)
  }, [value, duration])

  const formatted = isCurrency
    ? formatBRL(displayValue)
    : displayValue.toFixed(1)

  return (
    <span className={className}>
      {prefix}
      {formatted}
      {suffix}
    </span>
  )
}

