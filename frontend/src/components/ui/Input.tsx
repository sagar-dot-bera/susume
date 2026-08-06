import React, { forwardRef } from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, helperText, className = '', type = 'text', ...props }, ref) => {
    return (
      <div className="w-full flex flex-col gap-1.5 text-left">
        {label && (
          <label className="text-xs font-bold uppercase tracking-wider text-brand-primary font-heading select-none">
            {label}
          </label>
        )}
        <input
          type={type}
          ref={ref}
          className={`
            w-full bg-white text-text-primary px-4 py-2.5 
            border-2 border-brand-primary rounded-[4px] 
            transition-all duration-150 outline-none
            placeholder:text-text-muted font-sans text-sm
            focus:bg-brand-accent/15 focus:ring-0 focus:border-brand-primary
            disabled:bg-surface-alt disabled:text-text-muted disabled:cursor-not-allowed
            ${error ? 'border-brand-secondary bg-brand-secondary/5 focus:bg-brand-secondary/10' : ''}
            ${className}
          `}
          {...props}
        />
        {error && (
          <span className="text-xs text-brand-secondary font-bold font-sans">
            {error}
          </span>
        )}
        {!error && helperText && (
          <span className="text-xs text-text-secondary font-medium font-sans">
            {helperText}
          </span>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';
