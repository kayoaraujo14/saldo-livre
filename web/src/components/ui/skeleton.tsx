import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

export interface SkeletonProps extends HTMLAttributes<HTMLDivElement> {}

/**
 * Primitiva de esqueleto de carregamento com efeito Shimmer acelerado por GPU.
 * Utilizado para preencher o espaço de layout e evitar CLS (Cumulative Layout Shift).
 */
export function Skeleton({ className, ...props }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={cn('shimmer rounded-lg bg-white/[0.04]', className)}
      {...props}
    />
  )
}
