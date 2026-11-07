import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface EmptyStateAction {
  label: string
  onClick: () => void
  icon?: LucideIcon
  variant?: 'default' | 'outline' | 'ghost'
}

export interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description?: string
  action?: EmptyStateAction
  secondaryAction?: EmptyStateAction
  compact?: boolean
  className?: string
  iconTone?: 'emerald' | 'cyan' | 'amber' | 'slate'
  extraContent?: ReactNode
}

const TONE_STYLES = {
  emerald: {
    bg: 'bg-emerald-500/10',
    border: 'border-emerald-500/25',
    icon: 'text-emerald-400',
  },
  cyan: {
    bg: 'bg-cyan-500/10',
    border: 'border-cyan-500/25',
    icon: 'text-cyan-400',
  },
  amber: {
    bg: 'bg-amber-500/10',
    border: 'border-amber-500/25',
    icon: 'text-amber-400',
  },
  slate: {
    bg: 'bg-slate-800/50',
    border: 'border-slate-700/50',
    icon: 'text-slate-400',
  },
} as const

/**
 * Componente amigável de Estado Vazio (Empty State) para orientar o usuário
 * com feedback visual acolhedor e botões contextuais de ação de 1-clique.
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  secondaryAction,
  compact = false,
  className,
  iconTone = 'emerald',
  extraContent,
}: EmptyStateProps) {
  const tone = TONE_STYLES[iconTone]

  return (
    <div
      role="status"
      className={cn(
        'flex flex-col items-center justify-center text-center select-none animate-in fade-in zoom-in-95 duration-200',
        compact ? 'py-6 px-3' : 'py-12 px-4 sm:px-6',
        className
      )}
    >
      {/* Círculo com Ícone Translúcido em Destaque */}
      <div
        className={cn(
          'rounded-full flex items-center justify-center border shadow-xs transition-transform duration-300 hover:scale-105',
          tone.bg,
          tone.border,
          compact ? 'size-11 mb-2.5' : 'size-14 sm:size-16 mb-4'
        )}
      >
        <Icon className={cn(tone.icon, compact ? 'size-5' : 'size-7')} />
      </div>

      {/* Título Principal */}
      <h3
        className={cn(
          'font-bold text-slate-100 tracking-tight',
          compact ? 'text-xs sm:text-sm' : 'text-sm sm:text-base'
        )}
      >
        {title}
      </h3>

      {/* Descrição Explicativa */}
      {description && (
        <p
          className={cn(
            'text-slate-400 mt-1.5 max-w-sm leading-relaxed',
            compact ? 'text-[11px]' : 'text-xs sm:text-sm'
          )}
        >
          {description}
        </p>
      )}

      {/* Conteúdo Extra (ex: dicas ou badges adicionais) */}
      {extraContent && <div className="mt-3">{extraContent}</div>}

      {/* Botões de Ação de 1-Clique */}
      {(action || secondaryAction) && (
        <div
          className={cn(
            'flex flex-wrap items-center justify-center gap-2',
            compact ? 'mt-3' : 'mt-5'
          )}
        >
          {action && (
            <Button
              type="button"
              size={compact ? 'sm' : 'default'}
              variant={action.variant || 'default'}
              onClick={action.onClick}
              className="bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs h-8 sm:h-9 px-3.5 sm:px-4 rounded-xl cursor-pointer shadow-sm hover:shadow-emerald-500/20 active:scale-95 transition-all"
            >
              {action.icon && <action.icon className="size-3.5 mr-1.5 shrink-0" />}
              <span>{action.label}</span>
            </Button>
          )}

          {secondaryAction && (
            <Button
              type="button"
              size={compact ? 'sm' : 'default'}
              variant="outline"
              onClick={secondaryAction.onClick}
              className="text-xs text-slate-300 hover:text-white border-white/10 hover:bg-white/5 h-8 sm:h-9 px-3 rounded-xl cursor-pointer active:scale-95 transition-all"
            >
              {secondaryAction.icon && (
                <secondaryAction.icon className="size-3.5 mr-1.5 shrink-0" />
              )}
              <span>{secondaryAction.label}</span>
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
