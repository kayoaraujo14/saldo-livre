import * as React from 'react'
import { Slot } from 'radix-ui'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center justify-center rounded-full border px-2 py-0.5 text-xs font-medium w-fit whitespace-nowrap shrink-0 [&>svg]:size-3 gap-1 [&>svg]:pointer-events-none transition-colors overflow-hidden',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        destructive: 'border-transparent bg-destructive text-white',
        outline: 'text-foreground border-border',
        estimated: 'border-amber-400/30 bg-amber-400/10 text-amber-500 dark:text-amber-300',
        pending: 'border-orange-500/30 bg-orange-500/10 text-orange-600 dark:text-orange-300',
        paid: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
        info: 'border-sky-500/30 bg-sky-500/10 text-sky-600 dark:text-sky-300',
        violet: 'border-violet-500/30 bg-violet-500/10 text-violet-600 dark:text-violet-300',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

function Badge({
  className,
  variant,
  asChild = false,
  ...props
}: React.ComponentProps<'span'> & VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : 'span'
  return <Comp data-slot="badge" className={cn(badgeVariants({ variant }), className)} {...props} />
}

// eslint-disable-next-line react-refresh/only-export-components
export { Badge, badgeVariants }
