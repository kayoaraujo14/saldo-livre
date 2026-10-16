import * as React from 'react'
import { Popover as PopoverPrimitive, Tooltip as TooltipPrimitive, DropdownMenu as DropdownPrimitive } from 'radix-ui'
import { cn } from '@/lib/utils'

// ---------------- Popover ----------------
function Popover(props: React.ComponentProps<typeof PopoverPrimitive.Root>) {
  return <PopoverPrimitive.Root data-slot="popover" {...props} />
}
function PopoverTrigger(props: React.ComponentProps<typeof PopoverPrimitive.Trigger>) {
  return <PopoverPrimitive.Trigger data-slot="popover-trigger" {...props} />
}
function PopoverContent({
  className,
  align = 'center',
  sideOffset = 6,
  ...props
}: React.ComponentProps<typeof PopoverPrimitive.Content>) {
  return (
    <PopoverPrimitive.Portal>
      <PopoverPrimitive.Content
        data-slot="popover-content"
        align={align}
        sideOffset={sideOffset}
        className={cn(
          'bg-popover text-popover-foreground data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 z-50 w-72 origin-(--radix-popover-content-transform-origin) rounded-xl border p-4 shadow-xl outline-hidden',
          className,
        )}
        {...props}
      />
    </PopoverPrimitive.Portal>
  )
}

// ---------------- Tooltip ----------------
function TooltipProvider({ delayDuration = 150, ...props }: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return <TooltipPrimitive.Provider data-slot="tooltip-provider" delayDuration={delayDuration} {...props} />
}
function Tooltip(props: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  return <TooltipPrimitive.Root data-slot="tooltip" {...props} />
}
function TooltipTrigger(props: React.ComponentProps<typeof TooltipPrimitive.Trigger>) {
  return <TooltipPrimitive.Trigger data-slot="tooltip-trigger" {...props} />
}
function TooltipContent({
  className,
  sideOffset = 4,
  children,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        data-slot="tooltip-content"
        sideOffset={sideOffset}
        className={cn(
          'bg-foreground text-background animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 z-50 w-fit max-w-xs rounded-md px-3 py-1.5 text-xs text-balance',
          className,
        )}
        {...props}
      >
        {children}
      </TooltipPrimitive.Content>
    </TooltipPrimitive.Portal>
  )
}

// ---------------- Dropdown Menu ----------------
function DropdownMenu(props: React.ComponentProps<typeof DropdownPrimitive.Root>) {
  return <DropdownPrimitive.Root data-slot="dropdown-menu" {...props} />
}
function DropdownMenuTrigger(props: React.ComponentProps<typeof DropdownPrimitive.Trigger>) {
  return <DropdownPrimitive.Trigger data-slot="dropdown-menu-trigger" {...props} />
}
function DropdownMenuContent({
  className,
  sideOffset = 6,
  ...props
}: React.ComponentProps<typeof DropdownPrimitive.Content>) {
  return (
    <DropdownPrimitive.Portal>
      <DropdownPrimitive.Content
        data-slot="dropdown-menu-content"
        sideOffset={sideOffset}
        className={cn(
          'bg-popover text-popover-foreground data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 z-50 min-w-[10rem] overflow-hidden rounded-xl border p-1 shadow-xl',
          className,
        )}
        {...props}
      />
    </DropdownPrimitive.Portal>
  )
}
function DropdownMenuItem({ className, ...props }: React.ComponentProps<typeof DropdownPrimitive.Item>) {
  return (
    <DropdownPrimitive.Item
      data-slot="dropdown-menu-item"
      className={cn(
        "focus:bg-accent focus:text-accent-foreground relative flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-hidden select-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
        className,
      )}
      {...props}
    />
  )
}
function DropdownMenuLabel({ className, ...props }: React.ComponentProps<typeof DropdownPrimitive.Label>) {
  return <DropdownPrimitive.Label className={cn('px-2 py-1.5 text-xs text-muted-foreground', className)} {...props} />
}
function DropdownMenuSeparator({ className, ...props }: React.ComponentProps<typeof DropdownPrimitive.Separator>) {
  return <DropdownPrimitive.Separator className={cn('bg-border -mx-1 my-1 h-px', className)} {...props} />
}

export {
  Popover,
  PopoverTrigger,
  PopoverContent,
  TooltipProvider,
  Tooltip,
  TooltipTrigger,
  TooltipContent,
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
}
