import { useState, useMemo } from 'react'
import { formatBRL } from '@/lib/utils'
import { PieChart, BarChart3, TrendingUp, AlertTriangle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'

export interface ChartDataItem {
  label: string
  value: number
  color?: string
  sublabel?: string
}

export interface ChartConfig {
  type: 'donut' | 'pie' | 'bar' | 'line'
  title: string
  description?: string
  totalLabel?: string
  data: ChartDataItem[]
}

const DEFAULT_PALETTE = [
  '#10b981', // emerald
  '#6366f1', // indigo
  '#8b5cf6', // purple
  '#f59e0b', // amber
  '#06b6d4', // cyan
  '#ec4899', // pink
  '#3b82f6', // blue
  '#f43f5e', // rose
  '#14b8a6', // teal
]

export function ChatChart({ dataString }: { dataString: string }) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)

  const config = useMemo<ChartConfig | null>(() => {
    try {
      let clean = dataString.trim()
      // Remove possible markdown formatting if wrapped
      if (clean.startsWith('```json')) clean = clean.slice(7)
      if (clean.startsWith('```chart')) clean = clean.slice(8)
      if (clean.endsWith('```')) clean = clean.slice(0, -3)
      const parsed = JSON.parse(clean.trim())
      if (!parsed || !Array.isArray(parsed.data) || parsed.data.length === 0) return null

      const sanitizedData: ChartDataItem[] = parsed.data
        .map((d: any, idx: number) => ({
          label: String(d.label || `Item ${idx + 1}`),
          value: Math.max(0, Number(d.value) || 0),
          color: d.color || DEFAULT_PALETTE[idx % DEFAULT_PALETTE.length],
          sublabel: d.sublabel ? String(d.sublabel) : undefined,
        }))
        .filter((d: ChartDataItem) => d.value > 0)

      if (sanitizedData.length === 0) return null

      return {
        type: ['donut', 'pie', 'bar', 'line'].includes(parsed.type) ? parsed.type : 'donut',
        title: parsed.title || 'Análise Financeira',
        description: parsed.description,
        totalLabel: parsed.totalLabel,
        data: sanitizedData,
      }
    } catch {
      return null
    }
  }, [dataString])

  if (!config) {
    return (
      <div className="my-2 p-3 rounded-xl bg-slate-900/80 border border-slate-800 text-slate-400 text-xs flex items-center gap-2">
        <AlertTriangle className="size-4 text-amber-400 shrink-0" />
        <span>Não foi possível renderizar este gráfico (formato de dados inválido).</span>
      </div>
    )
  }

  const total = config.data.reduce((acc, curr) => acc + curr.value, 0)
  const maxValue = Math.max(...config.data.map((d) => d.value), 1)

  return (
    <div className="my-3.5 rounded-2xl bg-gradient-to-b from-slate-900/95 to-slate-950/95 border border-white/[0.1] shadow-xl overflow-hidden backdrop-blur-md">
      {/* Header */}
      <div className="p-3.5 pb-2.5 border-b border-white/[0.06] flex items-start justify-between gap-2">
        <div>
          <div className="flex items-center gap-1.5">
            {config.type === 'donut' || config.type === 'pie' ? (
              <PieChart className="size-4 text-emerald-400" />
            ) : config.type === 'bar' ? (
              <BarChart3 className="size-4 text-cyan-400" />
            ) : (
              <TrendingUp className="size-4 text-purple-400" />
            )}
            <h4 className="text-xs font-bold text-white tracking-tight">{config.title}</h4>
          </div>
          {config.description && (
            <p className="text-[11px] text-slate-400 mt-0.5 leading-snug">{config.description}</p>
          )}
        </div>

        <Badge
          variant="outline"
          className="text-[9px] px-1.5 py-0 bg-white/[0.04] text-slate-300 border-white/[0.1] shrink-0 font-medium"
        >
          {config.type === 'donut' || config.type === 'pie'
            ? 'Distribuição'
            : config.type === 'bar'
              ? 'Comparativo'
              : 'Evolução'}
        </Badge>
      </div>

      {/* Chart Body */}
      <div className="p-3.5">
        {config.type === 'donut' || config.type === 'pie' ? (
          <DonutChart
            data={config.data}
            total={total}
            totalLabel={config.totalLabel}
            hoveredIndex={hoveredIndex}
            onHover={setHoveredIndex}
          />
        ) : config.type === 'bar' ? (
          <BarChart
            data={config.data}
            total={total}
            maxValue={maxValue}
            hoveredIndex={hoveredIndex}
            onHover={setHoveredIndex}
          />
        ) : (
          <LineChart
            data={config.data}
            maxValue={maxValue}
            hoveredIndex={hoveredIndex}
            onHover={setHoveredIndex}
          />
        )}
      </div>

      {/* Legend & Breakdown Footer */}
      <div className="px-3.5 py-2.5 bg-black/20 border-t border-white/[0.05] flex flex-wrap gap-2 text-[10px]">
        {config.data.map((item, i) => {
          const pct = total > 0 ? (item.value / total) * 100 : 0
          const isHovered = hoveredIndex === i
          return (
            <div
              key={i}
              onMouseEnter={() => setHoveredIndex(i)}
              onMouseLeave={() => setHoveredIndex(null)}
              className={`flex items-center gap-1.5 px-2 py-1 rounded-lg transition-all cursor-pointer ${
                isHovered
                  ? 'bg-white/[0.12] ring-1 ring-white/20'
                  : 'bg-white/[0.03] hover:bg-white/[0.08]'
              }`}
            >
              <span
                className="size-2 rounded-full shrink-0"
                style={{ backgroundColor: item.color }}
              />
              <span className="font-medium text-slate-300">{item.label}:</span>
              <span className="font-semibold text-white">{formatBRL(item.value)}</span>
              <span className="text-slate-400">({pct.toFixed(1)}%)</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function DonutChart({
  data,
  total,
  totalLabel,
  hoveredIndex,
  onHover,
}: {
  data: ChartDataItem[]
  total: number
  totalLabel?: string
  hoveredIndex: number | null
  onHover: (idx: number | null) => void
}) {
  const size = 180
  const strokeWidth = 26
  const radius = (size - strokeWidth) / 2
  const center = size / 2
  const circumference = 2 * Math.PI * radius

  // Calculate cumulative offsets
  let accumulatedPct = 0
  const slices = data.map((d, index) => {
    const pct = total > 0 ? d.value / total : 0
    const strokeDasharray = `${pct * circumference} ${circumference}`
    const strokeDashoffset = -accumulatedPct * circumference
    accumulatedPct += pct
    return {
      ...d,
      pct,
      strokeDasharray,
      strokeDashoffset,
      index,
    }
  })

  const activeItem = hoveredIndex !== null ? data[hoveredIndex] : null

  return (
    <div className="flex flex-col sm:flex-row items-center justify-center gap-4 py-1">
      <div className="relative flex items-center justify-center shrink-0">
        <svg width={size} height={size} className="transform -rotate-90">
          {slices.map((slice) => {
            const isHovered = hoveredIndex === slice.index
            return (
              <circle
                key={slice.index}
                cx={center}
                cy={center}
                r={radius}
                fill="transparent"
                stroke={slice.color}
                strokeWidth={isHovered ? strokeWidth + 4 : strokeWidth}
                strokeDasharray={slice.strokeDasharray}
                strokeDashoffset={slice.strokeDashoffset}
                strokeLinecap="round"
                className="transition-all duration-300 cursor-pointer"
                style={{
                  filter: isHovered ? `drop-shadow(0 0 6px ${slice.color}88)` : undefined,
                  opacity: hoveredIndex !== null && !isHovered ? 0.45 : 1,
                }}
                onMouseEnter={() => onHover(slice.index)}
                onMouseLeave={() => onHover(null)}
              />
            )
          })}
        </svg>

        {/* Center Label */}
        <div className="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none px-4">
          <span className="text-[10px] text-slate-400 font-medium truncate max-w-[110px]">
            {activeItem ? activeItem.label : totalLabel || 'Total Geral'}
          </span>
          <span className="text-xs font-bold text-white tracking-tight mt-0.5">
            {formatBRL(activeItem ? activeItem.value : total)}
          </span>
          {activeItem && total > 0 && (
            <span className="text-[10px] font-semibold text-emerald-400">
              {((activeItem.value / total) * 100).toFixed(1)}%
            </span>
          )}
        </div>
      </div>
    </div>
  )
}

function BarChart({
  data,
  total,
  maxValue,
  hoveredIndex,
  onHover,
}: {
  data: ChartDataItem[]
  total: number
  maxValue: number
  hoveredIndex: number | null
  onHover: (idx: number | null) => void
}) {
  return (
    <div className="space-y-2.5 py-1">
      {data.map((item, i) => {
        const pctOfMax = maxValue > 0 ? (item.value / maxValue) * 100 : 0
        const pctOfTotal = total > 0 ? (item.value / total) * 100 : 0
        const isHovered = hoveredIndex === i

        return (
          <div
            key={i}
            onMouseEnter={() => onHover(i)}
            onMouseLeave={() => onHover(null)}
            className={`p-2 rounded-xl transition-all cursor-pointer ${
              isHovered ? 'bg-white/[0.08]' : 'bg-white/[0.02] hover:bg-white/[0.05]'
            }`}
          >
            <div className="flex items-center justify-between text-xs mb-1.5">
              <div className="flex items-center gap-1.5 min-w-0">
                <span
                  className="size-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: item.color }}
                />
                <span className="font-semibold text-slate-200 truncate">{item.label}</span>
                {item.sublabel && (
                  <span className="text-[10px] text-slate-500 truncate hidden xs:inline">
                    · {item.sublabel}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <span className="font-bold text-white text-xs">{formatBRL(item.value)}</span>
                <span className="text-[10px] text-slate-400 font-mono">
                  {pctOfTotal.toFixed(0)}%
                </span>
              </div>
            </div>

            {/* Progress Bar */}
            <div className="h-2 w-full bg-slate-800/80 rounded-full overflow-hidden p-0.5">
              <div
                className="h-full rounded-full transition-all duration-500"
                style={{
                  width: `${Math.max(pctOfMax, 3)}%`,
                  backgroundColor: item.color,
                  filter: isHovered ? `drop-shadow(0 0 6px ${item.color}99)` : undefined,
                }}
              />
            </div>
          </div>
        )
      })}
    </div>
  )
}

function LineChart({
  data,
  maxValue,
  hoveredIndex,
  onHover,
}: {
  data: ChartDataItem[]
  maxValue: number
  hoveredIndex: number | null
  onHover: (idx: number | null) => void
}) {
  const width = 360
  const height = 140
  const paddingX = 24
  const paddingY = 20

  const points = useMemo(() => {
    if (data.length <= 1) {
      return [{ x: width / 2, y: height / 2, ...data[0], index: 0 }]
    }
    const stepX = (width - paddingX * 2) / (data.length - 1)
    const effectiveMax = maxValue * 1.15 || 1

    return data.map((d, i) => {
      const x = paddingX + i * stepX
      const y = height - paddingY - (d.value / effectiveMax) * (height - paddingY * 2)
      return { x, y, ...d, index: i }
    })
  }, [data, maxValue])

  const polylinePoints = points.map((p) => `${p.x},${p.y}`).join(' ')
  const areaPoints = `${points[0]?.x || 0},${height - paddingY} ${polylinePoints} ${
    points[points.length - 1]?.x || width
  },${height - paddingY}`

  const activePoint = hoveredIndex !== null ? points[hoveredIndex] : null

  return (
    <div className="py-1">
      {/* Active tooltip indicator */}
      <div className="h-6 flex items-center justify-between text-xs px-1 mb-1">
        {activePoint ? (
          <div className="flex items-center gap-2">
            <span className="text-slate-400 text-[11px]">{activePoint.label}:</span>
            <span className="font-bold text-white text-xs">{formatBRL(activePoint.value)}</span>
          </div>
        ) : (
          <span className="text-[11px] text-slate-500">Passe o cursor sobre os pontos para detalhes</span>
        )}
      </div>

      <div className="relative w-full overflow-hidden">
        <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-auto overflow-visible">
          <defs>
            <linearGradient id="chartLineGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#10b981" stopOpacity="0.35" />
              <stop offset="100%" stopColor="#10b981" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Grid lines */}
          <line
            x1={paddingX}
            y1={height - paddingY}
            x2={width - paddingX}
            y2={height - paddingY}
            stroke="#ffffff15"
            strokeDasharray="3 3"
          />
          <line
            x1={paddingX}
            y1={paddingY}
            x2={width - paddingX}
            y2={paddingY}
            stroke="#ffffff10"
            strokeDasharray="3 3"
          />

          {/* Area under curve */}
          <polygon points={areaPoints} fill="url(#chartLineGrad)" />

          {/* Line */}
          <polyline
            points={polylinePoints}
            fill="none"
            stroke="#10b981"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Data Points */}
          {points.map((p) => {
            const isHovered = hoveredIndex === p.index
            return (
              <g key={p.index} className="cursor-pointer">
                <circle
                  cx={p.x}
                  cy={p.y}
                  r={isHovered ? 6 : 4}
                  fill={isHovered ? '#34d399' : '#10b981'}
                  stroke="#0f172a"
                  strokeWidth="2"
                  className="transition-all duration-200"
                  onMouseEnter={() => onHover(p.index)}
                  onMouseLeave={() => onHover(null)}
                />
              </g>
            )
          })}
        </svg>

        {/* Labels under X-axis */}
        <div className="flex justify-between text-[9px] text-slate-500 px-3 mt-1">
          <span>{data[0]?.label}</span>
          {data.length > 2 && (
            <span>{data[Math.floor(data.length / 2)]?.label}</span>
          )}
          <span>{data[data.length - 1]?.label}</span>
        </div>
      </div>
    </div>
  )
}
