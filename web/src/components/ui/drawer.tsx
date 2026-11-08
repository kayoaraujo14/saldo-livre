import * as React from 'react'
import { Drawer as VaulDrawer } from 'vaul'
import { XIcon } from 'lucide-react'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useIsMobile } from '@/hooks/useIsMobile'
import { cn } from '@/lib/utils'

interface ResponsiveModalContextValue {
  isMobile: boolean
}

const ResponsiveModalContext = React.createContext<ResponsiveModalContextValue>({
  isMobile: false,
})

function useResponsiveModal() {
  return React.useContext(ResponsiveModalContext)
}

export interface ResponsiveModalProps {
  open?: boolean
  onOpenChange?: (open: boolean) => void
  children?: React.ReactNode
  dismissible?: boolean
  nested?: boolean
}

/**
 * Componente adaptativo universal do Saldo Livre:
 * - Em Desktop (>= 640px): Renderiza Dialog centralizado do Radix.
 * - Em Telas Móveis (< 640px): Renderiza Bottom Sheet deslizante nativa do Vaul,
 *   com gesto de arrastar para fechar (drag-to-dismiss), momentum e proteção de teclado.
 */
export function ResponsiveModal({
  open,
  onOpenChange,
  children,
  dismissible = true,
  nested = false,
}: ResponsiveModalProps) {
  const isMobile = useIsMobile()

  return (
    <ResponsiveModalContext.Provider value={{ isMobile }}>
      {isMobile ? (
        <VaulDrawer.Root
          open={open}
          onOpenChange={onOpenChange}
          dismissible={dismissible}
          nested={nested}
          repositionInputs={true}
        >
          {children}
        </VaulDrawer.Root>
      ) : (
        <Dialog open={open} onOpenChange={onOpenChange}>
          {children}
        </Dialog>
      )}
    </ResponsiveModalContext.Provider>
  )
}

export interface ResponsiveModalContentProps
  extends React.HTMLAttributes<HTMLDivElement> {
  showCloseButton?: boolean
  showHandle?: boolean
}

export function ResponsiveModalContent({
  className,
  children,
  showCloseButton = true,
  showHandle = true,
  ...props
}: ResponsiveModalContentProps) {
  const { isMobile } = useResponsiveModal()

  if (!isMobile) {
    return (
      <DialogContent className={className} {...(props as React.ComponentProps<typeof DialogContent>)}>
        {children}
      </DialogContent>
    )
  }

  return (
    <VaulDrawer.Portal>
      <VaulDrawer.Overlay
        data-slot="drawer-overlay"
        className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"
      />
      <VaulDrawer.Content
        data-slot="drawer-content"
        className={cn(
          'fixed inset-x-0 bottom-0 z-50 flex flex-col rounded-t-[28px] rounded-b-none border-t border-white/[0.1] bg-[#0a0f1d]/98 text-slate-100 backdrop-blur-2xl shadow-2xl max-h-[92dvh] overflow-hidden focus:outline-hidden',
          className
        )}
        {...props}
      >
        {showHandle && (
          <div className="pt-2.5 pb-1 flex justify-center shrink-0">
            <VaulDrawer.Handle
              data-slot="drawer-handle"
              className="h-1.5 w-12 rounded-full bg-white/20 active:bg-white/40 cursor-grab active:cursor-grabbing transition-colors"
              aria-hidden="true"
            />
          </div>
        )}
        {children}
        {showCloseButton && (
          <VaulDrawer.Close
            data-slot="drawer-close"
            className="absolute top-3.5 right-4 rounded-full p-1.5 text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 transition-colors focus:outline-hidden cursor-pointer z-20"
          >
            <XIcon className="size-4" />
            <span className="sr-only">Fechar</span>
          </VaulDrawer.Close>
        )}
      </VaulDrawer.Content>
    </VaulDrawer.Portal>
  )
}

export function ResponsiveModalHeader({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  const { isMobile } = useResponsiveModal()

  if (!isMobile) {
    return <DialogHeader className={className} {...props} />
  }

  return (
    <div
      data-slot="drawer-header"
      className={cn('flex flex-col gap-1.5 text-left pr-8 p-4 shrink-0', className)}
      {...props}
    />
  )
}

export function ResponsiveModalFooter({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  const { isMobile } = useResponsiveModal()

  if (!isMobile) {
    return <DialogFooter className={className} {...props} />
  }

  return (
    <div
      data-slot="drawer-footer"
      className={cn(
        'flex flex-col-reverse gap-2 p-4 pt-2 border-t border-white/[0.08] bg-[#0a0f1d]/90 shrink-0 pb-[max(1rem,calc(env(safe-area-inset-bottom,0px)+0.75rem))]',
        className
      )}
      {...props}
    />
  )
}

export function ResponsiveModalTitle({
  className,
  ...props
}: React.ComponentProps<typeof DialogTitle>) {
  const { isMobile } = useResponsiveModal()

  if (!isMobile) {
    return <DialogTitle className={className} {...props} />
  }

  return (
    <VaulDrawer.Title
      data-slot="drawer-title"
      className={cn('text-lg font-bold text-white', className)}
      {...props}
    />
  )
}

export function ResponsiveModalDescription({
  className,
  ...props
}: React.ComponentProps<typeof DialogDescription>) {
  const { isMobile } = useResponsiveModal()

  if (!isMobile) {
    return <DialogDescription className={className} {...props} />
  }

  return (
    <VaulDrawer.Description
      data-slot="drawer-description"
      className={cn('text-xs text-slate-400', className)}
      {...props}
    />
  )
}

export function ResponsiveModalClose(
  props: React.ComponentProps<typeof DialogClose>
) {
  const { isMobile } = useResponsiveModal()

  if (!isMobile) {
    return <DialogClose {...props} />
  }

  return <VaulDrawer.Close {...props} />
}

// Aliases para conveniência e semântica de Drawer
export {
  ResponsiveModal as Drawer,
  ResponsiveModalContent as DrawerContent,
  ResponsiveModalHeader as DrawerHeader,
  ResponsiveModalFooter as DrawerFooter,
  ResponsiveModalTitle as DrawerTitle,
  ResponsiveModalDescription as DrawerDescription,
  ResponsiveModalClose as DrawerClose,
}
