import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Core spartan.ng class merging utility combining clsx and tailwind-merge.
 */
export function hlm(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
