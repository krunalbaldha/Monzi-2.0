'use client';

import React, { useEffect, useRef, useState } from 'react';

interface AnimatedNumberProps {
  /** Target value to count to */
  value: number;
  /** Duration in milliseconds (default 800) */
  duration?: number;
  /** Number of decimal places (default 0) */
  decimals?: number;
  /** Prefix shown before the number, e.g. "₹" */
  prefix?: string;
  /** Suffix shown after the number, e.g. "%" */
  suffix?: string;
  /** Custom formatter — overrides decimals/prefix/suffix */
  format?: (value: number) => string;
  className?: string;
}

/** Easing: ease-out cubic */
function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3);
}

export function AnimatedNumber({
  value,
  duration = 800,
  decimals = 0,
  prefix = '',
  suffix = '',
  format,
  className,
}: AnimatedNumberProps) {
  const [displayed, setDisplayed] = useState<number>(0);
  const startRef = useRef<number>(0);
  const startTimeRef = useRef<number | null>(null);
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    const startVal = startRef.current;
    const endVal = value;
    startTimeRef.current = null;

    function step(timestamp: number) {
      if (!startTimeRef.current) startTimeRef.current = timestamp;
      const elapsed = timestamp - startTimeRef.current;
      const progress = Math.min(elapsed / duration, 1);
      const eased = easeOutCubic(progress);
      const current = startVal + (endVal - startVal) * eased;
      setDisplayed(current);

      if (progress < 1) {
        rafRef.current = requestAnimationFrame(step);
      } else {
        startRef.current = endVal;
      }
    }

    if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(step);

    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [value, duration]);

  const formatted = format
    ? format(displayed)
    : `${prefix}${displayed.toFixed(decimals)}${suffix}`;

  return (
    <span className={className} aria-label={`${value}`}>
      {formatted}
    </span>
  );
}
